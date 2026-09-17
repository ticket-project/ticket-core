package com.ticket.booking.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.application.AdmissionGuard;
import com.ticket.booking.application.BookingAvailabilityChecker;
import com.ticket.booking.application.LockKey;
import com.ticket.booking.application.LockManager;
import com.ticket.booking.application.LockOptions;
import com.ticket.booking.application.PendingOrderCreator;
import com.ticket.booking.application.PerformanceSaleFinder;
import com.ticket.booking.domain.RequestedSeatIds;
import com.ticket.booking.domain.hold.Hold;
import com.ticket.booking.domain.hold.HoldManager;
import com.ticket.booking.domain.order.OrderRemainingTime;
import com.ticket.booking.domain.order.OrderState;
import com.ticket.booking.domain.salespolicy.PerformanceSalesPolicy;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.member.api.MemberLookupApi;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.api.PerformanceSaleCatalogApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 선택한 좌석으로 예매를 시작한다 — 좌석을 선점하고 결제를 기다리는 PENDING 주문을 만든다.
 *
 * <p>이 use case가 하는 일은 {@code Order} entity 하나를 만드는 것이 아니라 <b>예매 시작이라는 workflow를 조율</b>하는 것이다. 그래서
 * {@link #startBooking}을 읽으면 검증 → 좌석 선점 → 주문 생성 → 실패 시 보상이라는 업무 순서가 그대로 보인다. HTTP 계약은 {@code POST
 * /api/v1/orders}로 예전과 같다.
 *
 * <p><b>Redis 선점과 DB 주문은 하나의 트랜잭션이 아니다.</b> 이 method 전체에 {@code @Transactional}을 붙이지 않는다 — 그러면 다른
 * module 호출, 분산락, Redis 왕복이 전부 DB 트랜잭션 안에 들어가 connection을 오래 쥔다. 대신 경계를 셋으로 나눈다.
 *
 * <ul>
 *   <li>좌석 판매 상태 확인 — {@link BookingAvailabilityChecker}의 짧은 읽기 트랜잭션
 *   <li>좌석 선점 — Redis. 좌석 락은 이 구간에만 건다
 *   <li>주문 생성 — {@link PendingOrderCreator}의 짧은 쓰기 트랜잭션
 * </ul>
 *
 * <p>마지막 구간이 실패하면 이미 만들어진 Redis 선점을 되돌린다({@link #releaseHold}). 두 저장소를 하나의 ACID 트랜잭션으로 묶을 수 없으므로
 * 보상으로 맞춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartBookingUseCase {
    private static final LockOptions START_BOOKING_LOCK =
            LockOptions.defaults().withFailureMessage("주문 시작 처리 중입니다. 잠시 후 다시 시도해 주세요.");

    private final LockManager lockManager;
    private final PerformanceSaleFinder performanceSaleFinder;
    private final AdmissionGuard admissionGuard;
    private final MemberLookupApi memberLookup;
    private final BookingAvailabilityChecker bookingAvailabilityChecker;
    private final PerformanceSaleCatalogApi performanceSaleCatalog;
    private final HoldManager holdManager;
    private final PendingOrderCreator pendingOrderCreator;
    private final Clock clock;

    /** seatIds의 null·빈 목록·중복은 도메인 불변식이라 {@link RequestedSeatIds}가 판정한다. 여기서 다시 검사하지 않는다. */
    public record Input(
            Long performanceId,
            List<Long> seatIds,
            Long memberId,
            @Nullable String admissionToken) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }
    }

    public record Output(
            String orderKey, OrderState status, LocalDateTime expiresAt, long remainingSeconds) {}

    /** 같은 회원이 같은 회차의 예매를 동시에 시작하는 것을 직렬화한다. */
    public Output execute(final Input input) {
        return lockManager.withLock(
                List.of(LockKey.orderStart(input.memberId(), input.performanceId())),
                START_BOOKING_LOCK,
                () -> startBooking(input));
    }

    private Output startBooking(final Input input) {
        final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());
        final LocalDateTime now = LocalDateTime.now(clock);

        // 1. 지금 이 회차의 예매를 받을 수 있는가.
        final PerformanceSalesPolicy policy =
                performanceSaleFinder.requirePolicy(input.performanceId());
        policy.ensureAcceptingOrders(now);
        policy.ensureWithinHoldLimit(requestedSeatIds.size());
        admissionGuard.verifyIfRequired(
                policy, input.performanceId(), input.memberId(), input.admissionToken(), now);

        // 2. 예매할 수 있는 회원인가. JWT는 서명·만료만 보므로 탈퇴 회원은 여기서 걸러진다.
        memberLookup.requireActive(input.memberId());

        // 3. 예매할 수 있는 좌석인가. booking local DB만 보는 짧은 읽기 트랜잭션이다.
        final List<PerformanceSeat> performanceSeats =
                bookingAvailabilityChecker.check(
                        input.memberId(), input.performanceId(), requestedSeatIds);

        // 4. 주문에 남길 표시값(공연·공연장 이름, 등급, 좌석 라벨). 금액은 여기서 오지 않는다 -- 좌석 단가만 쓴다(ADR 0005).
        final PerformanceSaleSnapshot saleSnapshot =
                performanceSaleCatalog.getSaleSnapshot(
                        input.performanceId(), Set.copyOf(requestedSeatIds.toList()));

        // 5. 좌석을 선점한다(Redis). 좌석 락은 이 구간에만 건다 -- DB 트랜잭션 동안 쥐고 있으면
        //    connection 경합이 좌석 경합으로 번진다.
        final Duration holdDuration = policy.holdDuration();
        final List<LockKey> seatLocks =
                LockKey.seats(input.performanceId(), requestedSeatIds.toList());
        final Hold hold = holdSeats(seatLocks, input, requestedSeatIds, holdDuration, now);

        // 6. PENDING 주문을 만든다(DB 한 트랜잭션). 실패하면 5의 선점을 되돌린다.
        final String orderKey;
        try {
            orderKey =
                    pendingOrderCreator.create(
                            input.memberId(),
                            input.performanceId(),
                            holdDuration,
                            hold,
                            performanceSeats,
                            saleSnapshot);
        } catch (final RuntimeException exception) {
            releaseHold(seatLocks, hold, exception);
            throw exception;
        }

        return new Output(
                orderKey,
                OrderState.PENDING,
                hold.expiresAt(),
                OrderRemainingTime.seconds(
                        OrderState.PENDING, hold.expiresAt(), LocalDateTime.now(clock)));
    }

    /** 좌석 락을 건 구간 안에서만 Redis 선점을 만든다. 락은 이 구간을 벗어나지 않는다. */
    private Hold holdSeats(
            final List<LockKey> seatLocks,
            final Input input,
            final RequestedSeatIds requestedSeatIds,
            final Duration holdDuration,
            final LocalDateTime now) {
        return lockManager.withLock(
                seatLocks,
                LockOptions.defaults(),
                () ->
                        holdManager.createHold(
                                input.memberId(),
                                input.performanceId(),
                                requestedSeatIds,
                                holdDuration,
                                now));
    }

    /** 보상 실패가 원래 주문 생성 실패를 가리지 않도록, 해제 예외는 원인 예외에 suppressed로 붙이고 다시 던지지 않는다. */
    private void releaseHold(
            final List<LockKey> seatLocks,
            final Hold hold,
            final RuntimeException originalException) {
        try {
            lockManager.withLock(
                    seatLocks,
                    LockOptions.defaults(),
                    () ->
                            holdManager.release(
                                    hold.performanceId(), hold.holdKey(), hold.seatIds()));
        } catch (final RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.warn("hold 해제에 실패했습니다. holdKey={}", hold.holdKey(), releaseException);
        }
    }
}
