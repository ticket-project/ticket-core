package com.ticket.booking.order.usecase;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.order.domain.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpireOrderUseCase {
    private final OrderRepository orderRepository;
    private final OrderTerminationService orderTerminationService;

    @Transactional
    public void expireByOrderId(final Long orderId, final LocalDateTime now) {
        orderRepository
                .findPendingByIdForUpdate(orderId)
                .ifPresent(order -> orderTerminationService.expire(order, now));
    }

    @Transactional
    public void expireByHoldKey(final String holdKey, final LocalDateTime now) {
        orderRepository
                .findPendingByHoldKeyForUpdate(holdKey)
                .ifPresent(order -> orderTerminationService.expire(order, now));
    }
}
