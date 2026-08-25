package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.command.create.ValidatedOrderRequest;
import com.ticket.core.domain.order.command.create.RequestedSeatIds;
import com.ticket.core.domain.order.command.create.PendingOrderCreationResult;
import com.ticket.core.domain.order.command.create.HoldAllocator;
import com.ticket.core.domain.order.command.create.HoldAllocation;
import com.ticket.core.domain.hold.command.HoldCreationPostCommitNotifier;
import com.ticket.core.domain.order.OrderRemainingTime;
import com.ticket.core.support.lock.DistributedLock;
import com.ticket.core.domain.order.model.OrderState;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

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

    @DistributedLock(
            prefix = "start-order",
            dynamicKey = "#input.memberId() + ':' + #input.performanceId()",
            message = "주문 시작 처리 중입니다. 잠시 후 다시 시도해 주세요."
    )
    public Output execute(final Input input) {
        final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());
        final LocalDateTime now = LocalDateTime.now(clock);
        final ValidatedOrderRequest validated = validator.validate(input, requestedSeatIds, now);
        final Duration holdDuration = Duration.ofSeconds(validated.policy().holdTime());
        final HoldAllocation allocation = holdAllocator.allocate(
                input.memberId(),
                input.performanceId(),
                requestedSeatIds,
                validated.performanceSeats(),
                holdDuration,
                now
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
            releaseHold(allocation, e);
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

    private void releaseHold(final HoldAllocation allocation, final RuntimeException originalException) {
        try {
            holdAllocator.release(allocation);
        } catch (final RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.warn("hold 해제에 실패했습니다. holdKey={}", allocation.holdKey(), releaseException);
        }
    }
}
