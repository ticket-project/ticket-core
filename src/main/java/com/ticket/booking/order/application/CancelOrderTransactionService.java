package com.ticket.booking.order.application;

import com.ticket.booking.order.application.usecase.CancelOrderUseCase;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.exception.OrderNotOwnedException;
import com.ticket.booking.exception.OrderNotPendingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 주문 취소의 booking local DB 쓰기만 담당한다. {@link CancelOrderUseCase}가 member 공개 API를
 * 트랜잭션 밖에서 호출한 뒤, 이 component가 짧은 쓰기 트랜잭션 안에서 pending 주문 조회와 취소만
 * 수행한다.
 *
 * <p>package-private component로 분리한 이유는 self-invocation을 피하기 위해서다. 같은 클래스
 * 안에서 이 method를 호출하면 {@code @Transactional} proxy가 적용되지 않는다
 * ({@link PendingOrderLocalValidator}와 같은 이유).
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
        final Order order = orderRepository.findByOrderKeyAndMemberIdForUpdate(orderKey, memberId)
                .orElseThrow(() -> new OrderNotOwnedException());
        if (order.getStatus() != OrderState.PENDING) {
            throw new OrderNotPendingException();
        }
        return order;
    }
}
