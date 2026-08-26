package com.ticket.core.app.order.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.core.app.order.command.OrderTerminationService;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.order.model.OrderState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final MemberFinder memberFinder;
    private final OrderRepository orderRepository;
    private final OrderTerminationService orderTerminationService;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {}

    @Transactional
    public void execute(final Input input) {
        memberFinder.findActiveMemberById(input.memberId());
        final Order order = getPendingOwnedOrder(input.orderKey(), input.memberId());
        orderTerminationService.cancel(order, LocalDateTime.now(clock));
    }

    private Order getPendingOwnedOrder(final String orderKey, final Long memberId) {
        final Order order = orderRepository.findByOrderKeyAndMemberIdForUpdate(orderKey, memberId)
                .orElseThrow(() -> new CoreException(DomainErrorType.ORDER_NOT_OWNED));
        if (order.getStatus() != OrderState.PENDING) {
            throw new CoreException(DomainErrorType.ORDER_NOT_PENDING);
        }
        return order;
    }
}
