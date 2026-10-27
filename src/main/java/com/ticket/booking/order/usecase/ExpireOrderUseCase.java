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
     * 다른 인스턴스가 잠그지 않은 만료 주문 하나를 잠가 {@code EXPIRED}로 바꾸고 그 id를 돌려준다. 더 없으면 빈 값이다.
     *
     * @throws OrderExpirationFailedException 잠근 주문의 만료가 실패했다. 트랜잭션은 롤백되고, 호출자는 실린 id로 커서를 넘긴다
     */
    @Transactional
    public Optional<Long> expireNext(final LocalDateTime now, final long afterOrderId) {
        final Optional<Order> next = orderRepository.findNextExpirableForUpdateSkipLocked(now, afterOrderId);
        next.ifPresent(order -> {
            try {
                orderTerminationService.expire(order, now);
            } catch (final RuntimeException e) {
                throw new OrderExpirationFailedException(order.getId(), e);
            }
        });
        return next.map(Order::getId);
    }

    /**
     * 같은 회원·회차의 PENDING 주문이 만료 시각을 지났으면 그 자리에서 {@code EXPIRED}로 바꾼다. 새 주문이 그 주문 때문에 막혔을 때만 부른다 — 청소 전 몇 초 동안 원래 주인만 막히지
     * 않게 한다. 청소와 겹치면 행 락과 PENDING 조건으로 한쪽만 바꾼다.
     *
     * @return 만료시켰으면 true. PENDING이 없거나 아직 유효하면 false
     */
    @Transactional
    public boolean expireIfStale(final Long memberId, final Long performanceId, final LocalDateTime now) {
        return orderRepository
                .findPendingByMemberIdAndPerformanceIdForUpdate(memberId, performanceId)
                .filter(order -> order.isExpiredAt(now))
                .map(order -> {
                    orderTerminationService.expire(order, now);
                    return true;
                })
                .orElse(false);
    }

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
