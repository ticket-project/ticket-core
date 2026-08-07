package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.command.HoldCreationPostCommitNotifier;
import com.ticket.core.support.lock.DistributedLock;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
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

    public record Input(Long performanceId, List<Long> seatIds, Long memberId) {}

    public record Output(String orderKey, OrderState status, LocalDateTime expiresAt) {}

    @DistributedLock(
            prefix = "start-order",
            dynamicKey = "#input.memberId() + ':' + #input.performanceId()",
            message = "주문 시작 처리 중입니다. 잠시 후 다시 시도해 주세요."
    )
    public Output execute(final Input input) {
        final RequestedSeatIds requestedSeatIds = RequestedSeatIds.from(input.seatIds());
        final LocalDateTime now = LocalDateTime.now(clock);
        final PerformanceBookingPolicyView policy = validator.validate(input.memberId(), input.performanceId(), requestedSeatIds, now);
        final Duration holdDuration = Duration.ofSeconds(policy.holdTime());
        final HoldAllocation allocation = holdAllocator.allocate(
                input.memberId(),
                input.performanceId(),
                requestedSeatIds,
                holdDuration,
                now
        );
        final Order order;
        try {
            order = createPendingOrderTxService.create(
                    input.memberId(),
                    input.performanceId(),
                    holdDuration,
                    allocation
            );
        } catch (final RuntimeException e) {
            releaseHold(allocation, e);
            throw e;
        }
        notifyHoldCreated(allocation);
        return new Output(order.getOrderKey(), OrderState.PENDING, allocation.expiresAt());
    }

    private void notifyHoldCreated(final HoldAllocation allocation) {
        try {
            holdCreationPostCommitNotifier.notify(allocation.snapshot());
        } catch (final RuntimeException e) {
            log.warn("주문 생성 후처리를 제출하지 못했습니다. holdKey={}", allocation.holdKey(), e);
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
