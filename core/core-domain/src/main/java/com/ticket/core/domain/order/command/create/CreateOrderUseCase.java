package com.ticket.core.domain.order.command.create;

import com.ticket.core.support.lock.DistributedLock;
import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.event.HoldCreatedEvent;

import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.order.model.OrderState;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final OrderCreator orderCreator;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public record Input(Long performanceId, List<Long> seatIds, Long memberId) {}

    public record Output(String orderKey, OrderState status, LocalDateTime expiresAt) {}

    @Transactional
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
        try {
            return createOrder(input, holdDuration, allocation);
        } catch (final RuntimeException e) {
            releaseHold(allocation, e);
            throw e;
        }
    }

    private Output createOrder(final Input input, final Duration holdDuration, final HoldAllocation allocation) {
        final Order order = orderCreator.createPendingOrder(
                input.memberId(),
                input.performanceId(),
                allocation.holdKey(),
                allocation.expiresAt(),
                allocation.performanceSeats()
        );
        holdHistoryRecorder.recordCreated(
                input.memberId(),
                input.performanceId(),
                allocation.holdKey(),
                allocation.startedAt(holdDuration),
                allocation.expiresAt(),
                allocation.performanceSeats()
        );
        applicationEventPublisher.publishEvent(new HoldCreatedEvent(allocation.snapshot()));
        return new Output(order.getOrderKey(), OrderState.PENDING, allocation.expiresAt());
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
