package com.ticket.booking.order.application.usecase;

import com.ticket.booking.order.application.OrderTerminationService;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ExpireOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderTerminationService orderTerminationService;

    @Transactional
    public void expireByOrderId(final Long orderId, final LocalDateTime now) {
        expire(findPendingOrder(orderId), now);
    }

    @Transactional
    public void expireByHoldKey(final String holdKey, final LocalDateTime now) {
        expire(findPendingOrder(holdKey), now);
    }

    private void expire(final Order order, final LocalDateTime now) {
        if (order == null) {
            return;
        }
        orderTerminationService.expire(order, now);
    }

    private Order findPendingOrder(final Long orderId) {
        return orderRepository.findByIdAndStatusForUpdate(orderId, OrderState.PENDING)
                .orElse(null);
    }

    private Order findPendingOrder(final String holdKey) {
        return orderRepository.findByHoldKeyAndStatusForUpdate(holdKey, OrderState.PENDING)
                .orElse(null);
    }
}
