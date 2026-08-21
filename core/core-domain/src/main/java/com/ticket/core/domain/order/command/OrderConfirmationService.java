package com.ticket.core.domain.order.command;

import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import com.ticket.core.domain.order.OrderTerminationResult;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxWriter;
import com.ticket.core.domain.order.command.release.HoldReleaseRequestedEvent;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.repository.OrderSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.core.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OrderConfirmationService {

    private final OrderSeatRepository orderSeatRepository;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final HoldHistoryRecorder holdHistoryRecorder;
    private final HoldReleaseOutboxWriter holdReleaseOutboxWriter;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void confirm(final Order order, final LocalDateTime now) {
        final List<OrderSeat> orderSeats = loadAndValidateOrderSeats(order);
        final List<PerformanceSeat> performanceSeats = loadAvailablePerformanceSeats(orderSeats);

        order.confirm(now);
        performanceSeats.forEach(PerformanceSeat::reserve);
        holdHistoryRecorder.recordConfirmed(
                order.getMemberId(), order.getPerformanceId(), order.getHoldKey(), now, orderSeats
        );
        requestHoldRelease(order, orderSeats);
    }

    private List<OrderSeat> loadAndValidateOrderSeats(final Order order) {
        final List<OrderSeat> orderSeats =
                orderSeatRepository.findAllByOrder_IdOrderByIdAsc(order.getId());
        if (orderSeats.isEmpty()) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "확정할 주문 좌석이 없습니다.");
        }
        final boolean hasForeignOrderSeat = orderSeats.stream()
                .anyMatch(orderSeat -> !Objects.equals(orderSeat.getOrder().getId(), order.getId()));
        if (hasForeignOrderSeat) {
            throw new CoreException(ErrorType.INVALID_REQUEST, "orderSeats는 같은 order에 속해야 합니다.");
        }
        return orderSeats;
    }

    private List<PerformanceSeat> loadAvailablePerformanceSeats(final List<OrderSeat> orderSeats) {
        final List<Long> performanceSeatIds = orderSeats.stream()
                .map(OrderSeat::getPerformanceSeatId)
                .toList();
        final List<PerformanceSeat> performanceSeats =
                performanceSeatRepository.findAllById(performanceSeatIds);
        if (performanceSeats.size() != performanceSeatIds.size()) {
            throw new CoreException(ErrorType.SEAT_MISMATCH_IN_PERFORMANCE);
        }
        final boolean hasUnavailableSeat = performanceSeats.stream()
                .anyMatch(seat -> seat.getState() != PerformanceSeatState.AVAILABLE);
        if (hasUnavailableSeat) {
            throw new CoreException(ErrorType.SEAT_ALREADY_RESERVED);
        }
        return performanceSeats;
    }

    private void requestHoldRelease(final Order order, final List<OrderSeat> orderSeats) {
        final OrderTerminationResult result = new OrderTerminationResult(
                order.getPerformanceId(),
                order.getHoldKey(),
                orderSeats.stream().map(OrderSeat::getSeatId).toList()
        );
        final Long outboxId = holdReleaseOutboxWriter.append(result, HoldReleaseReason.PAYMENT_CONFIRMED);
        applicationEventPublisher.publishEvent(new HoldReleaseRequestedEvent(outboxId));
    }
}
