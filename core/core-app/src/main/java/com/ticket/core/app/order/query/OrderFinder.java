package com.ticket.core.app.order.query;

import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFinder {

    private final OrderRepository orderRepository;

    public Order findOwnedByOrderKey(final String orderKey, final Long memberId) {
        return orderRepository.findByOrderKeyAndMemberId(orderKey, memberId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_OWNED));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Order findPendingOwnedByOrderKeyForUpdate(final String orderKey, final Long memberId) {
        final Order order = orderRepository.findByOrderKeyAndMemberIdForUpdate(orderKey, memberId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_OWNED));
        if (order.getStatus() != OrderState.PENDING) {
            throw new CoreException(ErrorType.ORDER_NOT_PENDING);
        }
        return order;
    }
}
