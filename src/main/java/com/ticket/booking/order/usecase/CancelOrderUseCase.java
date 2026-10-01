package com.ticket.booking.order.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.OrderNotOwnedException;
import com.ticket.booking.exception.OrderNotPendingException;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

/** booking local 주문 조회와 취소를 하나의 짧은 쓰기 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {
    private final OrderRepository orderRepository;
    private final OrderTerminationService orderTerminationService;
    private final Clock clock;

    public record Input(String orderKey, Long memberId) {
        public Input {
            if (orderKey == null || orderKey.isBlank()) {
                throw new InvalidRequestException("orderKey는 필수입니다.");
            }
            memberId = requirePositiveId(memberId, "memberId");
        }
    }

    @Transactional
    public void execute(final Input input) {
        final Order order = orderRepository
                .findByOrderKeyAndMemberIdForUpdate(input.orderKey(), input.memberId())
                .orElseThrow(OrderNotOwnedException::new);
        if (order.getStatus() != OrderState.PENDING) {
            throw new OrderNotPendingException();
        }
        orderTerminationService.cancel(order, LocalDateTime.now(clock));
    }
}
