package com.ticket.booking.internal.application.order.command;

import com.ticket.booking.internal.domain.order.command.create.OrderKeyGenerator;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderSeat;
import com.ticket.booking.internal.domain.order.repository.OrderRepository;
import com.ticket.booking.internal.domain.order.repository.OrderSeatRepository;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCreator {

    private final OrderRepository orderRepository;
    private final OrderSeatRepository orderSeatRepository;
    private final OrderKeyGenerator orderKeyGenerator;

    @Transactional
    public Order createPendingOrder(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime expiresAt,
            final List<PerformanceSeat> performanceSeats
    ) {
        return create(memberId, performanceId, holdKey, expiresAt, performanceSeats);
    }

    private Order create(
            final Long memberId,
            final Long performanceId,
            final String holdKey,
            final LocalDateTime expiresAt,
            final List<PerformanceSeat> performanceSeats
    ) {
        final BigDecimal totalAmount = sumTotalAmount(performanceSeats);
        final String orderKey = orderKeyGenerator.generate();
        final Order order = orderRepository.save(new Order(memberId, performanceId, orderKey, holdKey, totalAmount, expiresAt));
        orderSeatRepository.saveAll(toOrderSeats(order, performanceSeats));
        return order;
    }

    private BigDecimal sumTotalAmount(final List<PerformanceSeat> performanceSeats) {
        return performanceSeats.stream()
                .map(PerformanceSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<OrderSeat> toOrderSeats(final Order order, final List<PerformanceSeat> performanceSeats) {
        return performanceSeats.stream()
                .map(seat -> new OrderSeat(order, seat.getId(), seat.getSeat().getId(), seat.getPrice()))
                .toList();
    }
}
