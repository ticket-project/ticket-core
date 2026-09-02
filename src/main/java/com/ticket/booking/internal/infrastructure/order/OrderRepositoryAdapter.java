package com.ticket.booking.internal.infrastructure.order;

import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderState;
import com.ticket.booking.internal.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link OrderRepository}의 JPA 구현이다. 비관적 락과 페이징 같은 기술 결정을 여기에 가둔다.
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderJpaRepository jpaRepository;

    @Override
    public Order save(final Order order) {
        return jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findByOrderKeyAndMemberIdForUpdate(final String orderKey, final Long memberId) {
        return jpaRepository.findByOrderKeyAndMemberIdForUpdate(orderKey, memberId);
    }

    @Override
    public boolean existsByMemberIdAndPerformanceIdAndStatus(
            final Long memberId,
            final Long performanceId,
            final OrderState status
    ) {
        return jpaRepository.existsByMemberIdAndPerformanceIdAndStatus(memberId, performanceId, status);
    }

    @Override
    public Optional<Order> findByHoldKeyAndStatusForUpdate(final String holdKey, final OrderState status) {
        return jpaRepository.findByHoldKeyAndStatusForUpdate(holdKey, status);
    }

    @Override
    public Optional<Order> findByIdAndStatusForUpdate(final Long orderId, final OrderState status) {
        return jpaRepository.findByIdAndStatusForUpdate(orderId, status);
    }

    @Override
    public List<Order> findExpirable(final OrderState status, final LocalDateTime expiresAt, final int limit) {
        return jpaRepository.findAllByStatusAndExpiresAtLessThanEqual(
                status,
                expiresAt,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id"))
        ).getContent();
    }
}
