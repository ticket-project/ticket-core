package com.ticket.core.app.order.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.app.event.HoldReleaseRequestedEvent;
import com.ticket.core.app.event.IntegrationEventPublisher;
import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.order.OrderTerminationResult;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.repository.OrderSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderTerminationService {

    private final OrderSeatRepository orderSeatRepository;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void cancel(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        order.cancel(now);
        holdHistoryRecorder.recordCanceled(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        requestHoldRelease(toResult(order, orderSeats), now);
    }

    public void expire(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        order.expire(now);
        holdHistoryRecorder.recordExpired(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        requestHoldRelease(toResult(order, orderSeats), now);
    }

    private List<OrderSeat> loadAndValidateOrderSeats(final Order order) {
        final List<OrderSeat> orderSeats =
                orderSeatRepository.findAllByOrderIdOrderByIdAsc(order.getId());
        final boolean hasForeignOrderSeat = orderSeats.stream()
                .anyMatch(orderSeat -> !Objects.equals(orderSeat.getOrder().getId(), order.getId()));
        if (hasForeignOrderSeat) {
            throw new CoreException(ApplicationErrorType.INVALID_INPUT, "orderSeats는 같은 order에 속해야 합니다.");
        }
        return orderSeats;
    }

    private OrderTerminationResult toResult(final Order order, final List<OrderSeat> orderSeats) {
        return new OrderTerminationResult(
                order.getPerformanceId(),
                order.getHoldKey(),
                orderSeats.stream().map(OrderSeat::getSeatId).toList()
        );
    }

    private void requestHoldRelease(final OrderTerminationResult result, final LocalDateTime now) {
        final Long eventId = integrationEventPublisher.publishHoldReleased(result, now);
        applicationEventPublisher.publishEvent(new HoldReleaseRequestedEvent(eventId));
    }
}
