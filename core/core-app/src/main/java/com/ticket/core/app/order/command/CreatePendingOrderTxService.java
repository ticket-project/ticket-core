package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.command.create.PendingOrderCreationResult;
import com.ticket.core.app.order.command.OrderCreator;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxWriter;
import com.ticket.core.domain.order.command.create.HoldAllocation;
import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.order.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CreatePendingOrderTxService {

    private final OrderCreator orderCreator;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final HoldCreationOutboxWriter holdCreationOutboxWriter;

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
        final Long postCommitOutboxId = holdCreationOutboxWriter.append(
                allocation.hold(),
                allocation.startedAt(holdDuration)
        );
        return new PendingOrderCreationResult(order, postCommitOutboxId);
    }
}
