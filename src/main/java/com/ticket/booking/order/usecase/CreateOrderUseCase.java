package com.ticket.booking.order.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ticket.booking.concurrency.DistributedLock;
import com.ticket.booking.concurrency.LockKey;
import com.ticket.booking.concurrency.LockOptions;
import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.order.domain.OrderRemainingTime;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.order.usecase.BookingPreflightReader.Preflight;
import com.ticket.booking.selection.domain.SeatSelectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 선택한 좌석으로 예매를 시작한다 — 좌석을 선점하고 결제를 기다리는 PENDING 주문을 만든다.
 *
 * <p>이 use case가 하는 일은 {@code Order} entity 하나를 만드는 것이 아니라 <b>주문 생성이라는 workflow를 조율</b>하는 것이다. 그래서 {@link #execute}를
 * 읽으면 검증 → 좌석 선점 → 주문 생성 → 실패 시 보상이라는 업무 순서가 그대로 보인다. HTTP 계약은 {@code POST /api/v1/orders}로 예전과 같다.
 *
 * <p><b>Redis 선점과 DB 주문은 하나의 트랜잭션이 아니다.</b> 이 method 전체에 {@code @Transactional}을 붙이지 않는다 — 그러면 분산락 대기와 Redis 왕복이 전부 DB
 * 트랜잭션 안에 들어가 connection을 오래 쥔다. 대신 경계를 셋으로 나눈다.
 *
 * <ul>
 *   <li>예매 전 확인(판매 정책·입장권·회원·진행 중 주문·좌석 판매 상태·표시값) — {@link BookingPreflightReader}의 짧은 읽기 트랜잭션
 *   <li>좌석 선점 — Redis. 좌석 락은 이 구간에만 건다
 *   <li>주문 생성 — {@link PendingOrderCreator}의 짧은 쓰기 트랜잭션
 * </ul>
 *
 * <p>마지막 구간이 실패하면 이미 만들어진 Redis 선점을 되돌린다({@link #releaseHold}). 두 저장소를 하나의 ACID 트랜잭션으로 묶을 수 없으므로 보상으로 맞춘다.
 *
 * <p><b>같은 회원·회차의 주문 생성을 락으로 줄 세우지 않는다.</b> 예전에는 흐름 전체를 회원·회차 분산 락으로 감쌌다(주문마다 Redis 왕복 3번, gatling-test capacity-log
 * 2026-10-19). 지금은 "회원·회차당 PENDING 주문 하나"를 DB 유일 인덱스({@code uk_orders_pending_member_performance})가 지키고, 같은 회원이 동시에 보낸
 * 두 번째 요청(더블클릭)은 어디서 부딪히든 예전과 같은 {@code E5004}를 받는다.
 *
 * <ul>
 *   <li>진행 중 주문 확인에서 — 앞 주문이 이미 저장됐다
 *   <li>좌석 선점에서 같은 좌석 — 선점 저장이 "같은 회원의 선점"을 알려 준다({@link HoldStore.SaveResult#HELD_BY_SAME_MEMBER})
 *   <li>본인 선택 확인에서 — 앞 주문의 후처리가 선택을 지웠다. 후처리는 앞 주문이 커밋된 뒤에만 돌므로 진행 중 주문을 다시 확인한다
 *   <li>주문 저장에서 다른 좌석 — 유일 인덱스가 막고, 이 요청의 선점을 되돌린다
 * </ul>
 *
 * 남는 차이: 앞 요청이 선점 뒤 주문 저장에서 실패하는 드문 경우, 그 사이에 온 요청도 {@code E5004}를 받는다(예전에는 기다렸다가 진행할 수 있었다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {
    private final DistributedLock distributedLock;
    private final BookingPreflightReader bookingPreflightReader;
    private final HoldRegistry holdRegistry;
    private final HoldStore holdStore;
    private final SeatSelectionService seatSelectionService;
    private final PendingOrderCreator pendingOrderCreator;
    private final OrderRepository orderRepository;
    private final Clock clock;

    /** seatIds의 null·빈 목록·중복은 도메인 불변식이라 {@link RequestedSeatIds}가 판정한다. 여기서 다시 검사하지 않는다. */
    public record Input(
            Long performanceId,
            List<Long> seatIds,
            Long memberId,
            @Nullable String admissionToken) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    public record Output(String orderKey, OrderState status, LocalDateTime expiresAt, long remainingSeconds) {}

    public Output execute(final Input input) {
        final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());
        final LocalDateTime now = LocalDateTime.now(clock);

        // 1. 예매를 시작해도 되는가. 판매 정책·입장권·회원·진행 중 주문·좌석 판매 상태를 확인하고 주문에 남길 표시값을 읽는다.
        //    connection을 한 번만 빌리는 읽기 트랜잭션이다.
        final Preflight preflight = bookingPreflightReader.read(
                input.memberId(), input.performanceId(), requestedSeatIds, input.admissionToken(), now);

        // 2. 본인이 선택 중인 좌석만 선점한다(Redis). 좌석 락은 이 구간에만 건다 -- DB 트랜잭션 동안 쥐고 있으면
        //    connection 경합이 좌석 경합으로 번진다.
        final Duration holdDuration = preflight.policy().holdDuration();
        final List<LockKey> seatLocks = LockKey.seats(input.performanceId(), requestedSeatIds.toList());
        final Hold hold;
        try {
            hold = holdSeats(seatLocks, input, requestedSeatIds, holdDuration, now);
        } catch (final BookingException exception) {
            throw pendingOrderErrorIfStarted(input, exception);
        }

        // 3. PENDING 주문을 만든다(DB 한 트랜잭션). 실패하면 2의 선점을 되돌린다.
        final String orderKey;
        try {
            orderKey = pendingOrderCreator.create(
                    input.memberId(),
                    input.performanceId(),
                    holdDuration,
                    hold,
                    preflight.performanceSeats(),
                    preflight.saleSnapshot());
        } catch (final DataIntegrityViolationException exception) {
            // 같은 회원·회차의 PENDING 주문이 그사이 생겼다(uk_orders_pending_member_performance). ORDERS의 다른 유니크(order_key, hold_key)는
            // 요청마다 새 UUID라 여기 걸리지 않는다.
            releaseHold(seatLocks, hold, exception);
            throw new BookingException(BookingErrorCode.E5004);
        } catch (final RuntimeException exception) {
            releaseHold(seatLocks, hold, exception);
            throw exception;
        }

        return new Output(
                orderKey,
                OrderState.PENDING,
                hold.expiresAt(),
                OrderRemainingTime.seconds(OrderState.PENDING, hold.expiresAt(), LocalDateTime.now(clock)));
    }

    /**
     * 좌석 락을 건 구간 안에서만 Redis 선점을 만든다. 락은 이 구간을 벗어나지 않는다.
     *
     * <p>본인이 선택 중인 좌석으로만 선점한다(ADR 0021). 선택·해제도 같은 좌석 락을 잡으므로 확인과 선점 사이에 선택이 바뀌지 않는다 — 락 밖에서 확인하면 그 사이 선택이 해제되고 남이 다시
     * 고른 좌석을 선점할 수 있다.
     */
    private Hold holdSeats(
            final List<LockKey> seatLocks,
            final Input input,
            final RequestedSeatIds requestedSeatIds,
            final Duration holdDuration,
            final LocalDateTime now) {
        return distributedLock.withLock(seatLocks, LockOptions.defaults(), () -> {
            seatSelectionService.requireSelectedBy(input.performanceId(), input.memberId(), requestedSeatIds.toList());
            return holdRegistry.createHold(
                    input.memberId(), input.performanceId(), requestedSeatIds.toList(), holdDuration, now);
        });
    }

    /** 본인 선택 확인에 실패했는데 같은 회원·회차의 PENDING 주문이 있으면 진행 중 주문 오류로 바꾼다. 앞 주문의 후처리가 선택을 지운 경우다. 실패한 요청에서만 DB를 한 번 더 읽는다. */
    private BookingException pendingOrderErrorIfStarted(final Input input, final BookingException exception) {
        final BookingErrorCode code = exception.getErrorCode();
        if ((code == BookingErrorCode.E4006 || code == BookingErrorCode.E4007)
                && orderRepository.existsPendingByMemberIdAndPerformanceId(input.memberId(), input.performanceId())) {
            return new BookingException(BookingErrorCode.E5004);
        }
        return exception;
    }

    /** 보상 실패가 원래 주문 생성 실패를 가리지 않도록, 해제 예외는 원인 예외에 suppressed로 붙이고 다시 던지지 않는다. */
    private void releaseHold(final List<LockKey> seatLocks, final Hold hold, final RuntimeException originalException) {
        try {
            distributedLock.withLock(
                    seatLocks,
                    LockOptions.defaults(),
                    () -> holdStore.release(hold.performanceId(), hold.holdKey(), hold.seatIds()));
        } catch (final RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.warn("hold 해제에 실패했습니다. holdKey={}", hold.holdKey(), releaseException);
        }
    }
}
