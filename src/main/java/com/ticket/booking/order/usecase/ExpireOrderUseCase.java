package com.ticket.booking.order.usecase;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;

import lombok.RequiredArgsConstructor;

/** 주문 하나를 만료한다. 주문마다 트랜잭션 하나다 — 한 주문의 실패가 다른 주문의 만료를 막지 않는다. */
@Service
@RequiredArgsConstructor
public class ExpireOrderUseCase {
    private final OrderRepository orderRepository;
    private final OrderTerminationService orderTerminationService;

    /**
     * 다른 인스턴스가 잠그지 않은 만료 주문 하나를 잠가 {@code EXPIRED}로 바꾸고 그 id와 만료 시각을 돌려준다. 더 없으면 빈 값이다.
     *
     * @throws OrderExpirationFailedException 잠근 주문의 만료가 실패했다. 트랜잭션은 롤백되고, 호출자는 실린 id로 커서를 넘긴다
     */
    @Transactional
    public Optional<ExpiredOrder> expireNext(final LocalDateTime now, final long afterOrderId) {
        final Optional<Order> next = orderRepository.findNextExpirableForUpdateSkipLocked(now, afterOrderId);
        next.ifPresent(order -> {
            try {
                orderTerminationService.expire(order, now);
            } catch (final RuntimeException e) {
                throw new OrderExpirationFailedException(order.getId(), e);
            }
        });
        return next.map(order -> new ExpiredOrder(order.getId(), order.getExpiresAt()));
    }

    /** 만료시킨 주문. 청소가 커서로 쓰는 id와, 만료 시각 뒤 얼마 만에 처리했는지 재는 만료 시각이다. */
    public record ExpiredOrder(long orderId, LocalDateTime expiresAt) {}

    /** 잠근 주문의 만료가 실패했다. 청소가 이 주문을 지나 뒤의 주문으로 넘어가도록 id를 싣는다. */
    public static final class OrderExpirationFailedException extends RuntimeException {
        private final long orderId;

        OrderExpirationFailedException(final long orderId, final RuntimeException cause) {
            super("주문 만료에 실패했습니다. orderId=" + orderId, cause);
            this.orderId = orderId;
        }

        public long orderId() {
            return orderId;
        }
    }
}
