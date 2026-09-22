package com.ticket.booking.order.usecase;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;

import lombok.RequiredArgsConstructor;

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

    private void expire(final @Nullable Order order, final LocalDateTime now) {
        if (order == null) {
            return;
        }
        orderTerminationService.expire(order, now);
    }

    private @Nullable Order findPendingOrder(final Long orderId) {
        return orderRepository
                .findByIdAndStatusForUpdate(orderId, OrderState.PENDING)
                .orElse(null);
    }

    private @Nullable Order findPendingOrder(final String holdKey) {
        return orderRepository
                .findByHoldKeyAndStatusForUpdate(holdKey, OrderState.PENDING)
                .orElse(null);
    }
}
