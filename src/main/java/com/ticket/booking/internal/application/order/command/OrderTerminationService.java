package com.ticket.booking.internal.application.order.command;

import com.ticket.booking.OrderTerminated;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.booking.internal.domain.hold.command.HoldHistoryRecorder;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderSeat;
import com.ticket.booking.internal.domain.order.repository.OrderSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderTerminationService {

    private final OrderSeatRepository orderSeatRepository;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public void cancel(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        order.cancel(now);
        holdHistoryRecorder.recordCanceled(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        publishTerminated(order, orderSeats, now);
    }

    public void expire(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        order.expire(now);
        holdHistoryRecorder.recordExpired(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        publishTerminated(order, orderSeats, now);
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

    private void publishTerminated(final Order order, final List<OrderSeat> orderSeats, final LocalDateTime now) {
        eventPublisher.publishEvent(new OrderTerminated(
                UUID.randomUUID(),
                OrderTerminated.SCHEMA_VERSION,
                order.getId(),
                order.getMemberId(),
                order.getHoldKey(),
                performanceSeatIds(orderSeats),
                order.getStatus().name(),
                now.atZone(clock.getZone()).toInstant()
        ));
    }

    private Set<Long> performanceSeatIds(final List<OrderSeat> orderSeats) {
        return orderSeats.stream()
                .map(OrderSeat::getPerformanceSeatId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
