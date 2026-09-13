package com.ticket.booking.order.application.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ticket.booking.common.LockKey;
import com.ticket.booking.common.LockManager;
import com.ticket.booking.common.LockOptions;
import com.ticket.booking.common.RequestedSeatIds;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.order.application.CreateOrderPreparer;
import com.ticket.booking.order.application.CreatePendingOrderTransactionService;
import com.ticket.booking.order.application.ValidatedOrderContext;
import com.ticket.booking.order.domain.OrderRemainingTime;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {
    private static final LockOptions START_ORDER_LOCK =
            LockOptions.defaults().withFailureMessage("주문 시작 처리 중입니다. 잠시 후 다시 시도해 주세요.");
    private final LockManager lockManager;
    private final CreateOrderPreparer preparer;
    private final HoldManager holdManager;
    private final CreatePendingOrderTransactionService createPendingOrderTransactionService;
    private final Clock clock;

    /** seatIds의 null·빈 목록·중복은 도메인 불변식이라 {@link RequestedSeatIds}가 판정한다. 여기서 다시 검사하지 않는다. */
    public record Input(
            Long performanceId, List<Long> seatIds, Long memberId, String admissionToken) {
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

    /**
     * 같은 회원과 회차의 주문 시작을 직렬화한 뒤 좌석을 선점하고 PENDING 주문을 만든다.
     *
     * <p>좌석 락은 Redis hold를 만드는 구간에만 건다. DB 트랜잭션 동안 좌석 락을 쥐고 있으면 connection 경합이 좌석 경합으로 번진다.
     *
     * <p>조립은 이 use case가 직접 한다 — {@link HoldManager}를 그대로 호출하고 이미 확보한 좌석 목록을 함께 넘긴다. 호출 한 번을 감싸 결과를
     * 다시 포장하기만 하는 중간 계층을 두지 않는다.
     */
    public Output execute(final Input input) {
        return lockManager.withLock(
                List.of(LockKey.orderStart(input.memberId(), input.performanceId())),
                START_ORDER_LOCK,
                () -> createOrder(input));
    }

    private Output createOrder(final Input input) {
        final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());
        final LocalDateTime now = LocalDateTime.now(clock);
        final ValidatedOrderContext prepared = preparer.prepare(input, requestedSeatIds, now);
        final Duration holdDuration = prepared.policy().holdDuration();
        final List<LockKey> seatLocks =
                LockKey.seats(input.performanceId(), requestedSeatIds.toList());

        final Hold hold =
                lockManager.withLock(
                        seatLocks,
                        LockOptions.defaults(),
                        () ->
                                holdManager.createHold(
                                        input.memberId(),
                                        input.performanceId(),
                                        requestedSeatIds,
                                        holdDuration,
                                        now));

        final String orderKey;
        try {
            orderKey =
                    createPendingOrderTransactionService.create(
                            input.memberId(),
                            input.performanceId(),
                            holdDuration,
                            hold,
                            prepared.performanceSeats(),
                            prepared.saleSnapshot());
        } catch (final RuntimeException e) {
            releaseHold(seatLocks, hold, e);
            throw e;
        }
        return new Output(
                orderKey,
                OrderState.PENDING,
                hold.expiresAt(),
                OrderRemainingTime.seconds(
                        OrderState.PENDING, hold.expiresAt(), LocalDateTime.now(clock)));
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
