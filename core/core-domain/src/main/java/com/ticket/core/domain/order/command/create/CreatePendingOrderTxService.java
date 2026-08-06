package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.event.HoldCreatedEvent;
import com.ticket.core.domain.order.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CreatePendingOrderTxService {

    private final OrderCreator orderCreator;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public Order create(
            final Long memberId,
            final Long performanceId,
            final Duration holdDuration,
            final HoldAllocation allocation
    ) {
        final Order order = orderCreator.createPendingOrder(
                memberId,
                performanceId,
                allocation.holdKey(),
                allocation.expiresAt(),
                allocation.performanceSeats()
        );
        holdHistoryRecorder.recordCreated(
                memberId,
                performanceId,
                allocation.holdKey(),
                allocation.startedAt(holdDuration),
                allocation.expiresAt(),
                allocation.performanceSeats()
        );
        applicationEventPublisher.publishEvent(new HoldCreatedEvent(allocation.snapshot()));
        return order;
    }
}
