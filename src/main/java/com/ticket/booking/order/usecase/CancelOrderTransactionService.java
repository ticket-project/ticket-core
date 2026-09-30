package com.ticket.booking.order.usecase;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.exception.OrderNotOwnedException;
import com.ticket.booking.exception.OrderNotPendingException;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;

import lombok.RequiredArgsConstructor;

/**
 * 주문 취소의 booking local DB 쓰기만 담당한다. 짧은 쓰기 트랜잭션 안에서 pending 주문 조회와 취소만 수행하고, {@link CancelOrderUseCase}는 여기에 위임만 한다.
 *
 * <p>같은 클래스 안에서 {@code @Transactional} method를 호출하면 proxy가 적용되지 않는다 ({@link BookingAvailabilityChecker}와 같은 이유). 그래서
 * 트랜잭션 경계를 이 component가 갖는다.
 */
@Component
@RequiredArgsConstructor
public class CancelOrderTransactionService {
    private final OrderRepository orderRepository;
    private final OrderTerminationService orderTerminationService;
    private final Clock clock;

    @Transactional
    public void cancel(final String orderKey, final Long memberId) {
        final Order order = getPendingOwnedOrder(orderKey, memberId);
        orderTerminationService.cancel(order, LocalDateTime.now(clock));
    }

    private Order getPendingOwnedOrder(final String orderKey, final Long memberId) {
        final Order order = orderRepository
                .findByOrderKeyAndMemberIdForUpdate(orderKey, memberId)
                .orElseThrow(OrderNotOwnedException::new);
        if (order.getStatus() != OrderState.PENDING) {
            throw new OrderNotPendingException();
        }
        return order;
    }
}
