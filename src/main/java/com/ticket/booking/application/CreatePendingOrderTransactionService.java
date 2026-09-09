package com.ticket.booking.application;

import com.ticket.booking.OrderStarted;
import com.ticket.booking.domain.PendingOrderCreationResult;
import com.ticket.booking.application.OrderCreator;
import com.ticket.booking.domain.HoldAllocation;
import com.ticket.booking.domain.HoldHistoryRecorder;
import com.ticket.booking.domain.Order;
import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.show.PerformanceSaleSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CreatePendingOrderTransactionService {

    private final OrderCreator orderCreator;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public PendingOrderCreationResult create(
            final Long memberId,
            final Long performanceId,
            final Duration holdDuration,
            final HoldAllocation allocation,
            final PerformanceSaleSnapshot saleSnapshot
    ) {
        final Order order = orderCreator.createPendingOrder(
                memberId,
                performanceId,
                allocation.holdKey(),
                allocation.expiresAt(),
                allocation.performanceSeats(),
                saleSnapshot
        );
        final LocalDateTime startedAt = allocation.startedAt(holdDuration);
        holdHistoryRecorder.recordCreated(
                memberId,
                performanceId,
                allocation.holdKey(),
                startedAt,
                allocation.expiresAt(),
                allocation.performanceSeats()
        );
        eventPublisher.publishEvent(new OrderStarted(
                UUID.randomUUID(),
                OrderStarted.SCHEMA_VERSION,
                order.getId(),
                memberId,
                allocation.holdKey(),
                performanceSeatIds(allocation.performanceSeats()),
                startedAt.atZone(clock.getZone()).toInstant()
        ));
        return new PendingOrderCreationResult(order);
    }

    private Set<Long> performanceSeatIds(final List<PerformanceSeat> performanceSeats) {
        return performanceSeats.stream()
                .map(PerformanceSeat::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
