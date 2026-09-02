package com.ticket.booking.internal.application.order.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.application.event.HoldReleaseRequestedEvent;
import com.ticket.booking.internal.application.event.HoldLifecycleEventPublisher;
import com.ticket.booking.internal.application.event.HoldReleaseRequest;
import com.ticket.booking.internal.domain.hold.command.HoldHistoryRecorder;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderSeat;
import com.ticket.booking.internal.domain.order.repository.OrderSeatRepository;
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
    private final HoldLifecycleEventPublisher holdLifecycleEventPublisher;
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
            throw new CoreException(ErrorType.INVALID_REQUEST, "orderSeats는 같은 order에 속해야 합니다.");
        }
        return orderSeats;
    }

    private HoldReleaseRequest toResult(final Order order, final List<OrderSeat> orderSeats) {
        return new HoldReleaseRequest(
                order.getPerformanceId(),
                order.getHoldKey(),
                orderSeats.stream().map(OrderSeat::getSeatId).toList()
        );
    }

    private void requestHoldRelease(final HoldReleaseRequest request, final LocalDateTime now) {
        final Long eventId = holdLifecycleEventPublisher.publishHoldReleased(request, now);
        applicationEventPublisher.publishEvent(new HoldReleaseRequestedEvent(eventId));
    }
}
