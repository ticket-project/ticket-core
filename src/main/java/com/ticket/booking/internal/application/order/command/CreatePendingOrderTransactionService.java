package com.ticket.booking.internal.application.order.command;

import com.ticket.booking.internal.application.event.HoldLifecycleEventPublisher;
import com.ticket.booking.internal.domain.order.command.create.PendingOrderCreationResult;
import com.ticket.booking.internal.application.order.command.OrderCreator;
import com.ticket.booking.internal.domain.order.command.create.HoldAllocation;
import com.ticket.booking.internal.domain.hold.command.HoldHistoryRecorder;
import com.ticket.booking.internal.domain.order.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CreatePendingOrderTransactionService {

    private final OrderCreator orderCreator;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final HoldLifecycleEventPublisher holdLifecycleEventPublisher;

    @Transactional
    public PendingOrderCreationResult create(
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
        final Long postCommitOutboxId = holdLifecycleEventPublisher.publishHoldCreated(
                allocation.hold(),
                allocation.startedAt(holdDuration)
        );
        return new PendingOrderCreationResult(order, postCommitOutboxId);
    }
}
