package com.ticket.core.app.order.command;

import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.LockManager;
import com.ticket.core.app.lock.LockOptions;
import com.ticket.core.domain.hold.command.HoldCreationPostCommitNotifier;
import com.ticket.core.domain.order.OrderRemainingTime;
import com.ticket.core.domain.order.command.create.HoldAllocation;
import com.ticket.core.domain.order.command.create.HoldAllocator;
import com.ticket.core.domain.order.command.create.PendingOrderCreationResult;
import com.ticket.core.domain.order.command.create.RequestedSeatIds;
import com.ticket.core.domain.order.command.create.ValidatedOrderRequest;
import com.ticket.core.domain.order.model.OrderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private static final LockOptions START_ORDER_LOCK = LockOptions.defaults()
            .withFailureMessage("주문 시작 처리 중입니다. 잠시 후 다시 시도해 주세요.");

    private final LockManager lockManager;
    private final CreateOrderValidator validator;
    private final HoldAllocator holdAllocator;
    private final CreatePendingOrderTxService createPendingOrderTxService;
    private final HoldCreationPostCommitNotifier holdCreationPostCommitNotifier;
    private final Clock clock;

    public record Input(Long performanceId, List<Long> seatIds, Long memberId, String admissionToken) {}

    public record Output(
            String orderKey,
            OrderState status,
            LocalDateTime expiresAt,
            long remainingSeconds
    ) {}

    /**
     * 같은 회원과 회차의 주문 시작을 직렬화한 뒤 좌석을 선점하고 PENDING 주문을 만든다.
     *
     * <p>좌석 락은 Redis hold를 만드는 구간에만 건다. DB 트랜잭션 동안 좌석 락을 쥐고 있으면
     * connection 경합이 좌석 경합으로 번진다.
     */
    public Output execute(final Input input) {
        return lockManager.withLock(
                List.of(LockKey.orderStart(input.memberId(), input.performanceId())),
                START_ORDER_LOCK,
                () -> createOrder(input)
        );
    }

    private Output createOrder(final Input input) {
        final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());
        final LocalDateTime now = LocalDateTime.now(clock);
        final ValidatedOrderRequest validated = validator.validate(input, requestedSeatIds, now);
        final Duration holdDuration = Duration.ofSeconds(validated.policy().holdTime());
        final List<LockKey> seatLocks = LockKey.seats(input.performanceId(), requestedSeatIds.toList());

        final HoldAllocation allocation = lockManager.withLock(
                seatLocks,
                LockOptions.defaults(),
                () -> holdAllocator.allocate(
                        input.memberId(),
                        input.performanceId(),
                        requestedSeatIds,
                        validated.performanceSeats(),
                        holdDuration,
                        now
                )
        );

        final PendingOrderCreationResult creationResult;
        try {
            creationResult = createPendingOrderTxService.create(
                    input.memberId(),
                    input.performanceId(),
                    holdDuration,
                    allocation
            );
        } catch (final RuntimeException e) {
            releaseHold(seatLocks, allocation, e);
            throw e;
        }
        notifyHoldCreated(creationResult.postCommitOutboxId(), allocation.holdKey());
        return new Output(
                creationResult.order().getOrderKey(),
                OrderState.PENDING,
                allocation.expiresAt(),
                OrderRemainingTime.seconds(OrderState.PENDING, allocation.expiresAt(), LocalDateTime.now(clock))
        );
    }

    private void notifyHoldCreated(final Long outboxId, final String holdKey) {
        try {
            holdCreationPostCommitNotifier.notify(outboxId);
        } catch (final RuntimeException e) {
            log.warn("주문 생성 후처리를 제출하지 못했습니다. holdKey={}", holdKey, e);
        }
    }

    private void releaseHold(
            final List<LockKey> seatLocks,
            final HoldAllocation allocation,
            final RuntimeException originalException
    ) {
        try {
            lockManager.withLock(seatLocks, LockOptions.defaults(), () -> holdAllocator.release(allocation));
        } catch (final RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.warn("hold 해제에 실패했습니다. holdKey={}", allocation.holdKey(), releaseException);
        }
    }
}
