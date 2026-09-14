package com.ticket.booking.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.ticket.booking.domain.order.Order;
import com.ticket.booking.domain.order.OrderRepository;
import com.ticket.booking.domain.order.OrderState;

import lombok.RequiredArgsConstructor;

/** {@link OrderRepository}의 JPA 구현이다. 비관적 락과 페이징 같은 기술 결정을 여기에 가둔다. */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {
    private final SpringDataOrderJpaRepository jpaRepository;

    @Override
    public Order save(final Order order) {
        return jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(final Long orderId) {
        return jpaRepository.findById(orderId);
    }

    @Override
    public Optional<Order> findByOrderKeyAndMemberIdForUpdate(
            final String orderKey, final Long memberId) {
        return jpaRepository.findByOrderKeyAndMemberIdForUpdate(orderKey, memberId);
    }

    @Override
    public boolean existsByMemberIdAndPerformanceIdAndStatus(
            final Long memberId, final Long performanceId, final OrderState status) {
        return jpaRepository.existsByMemberIdAndPerformanceIdAndStatus(
                memberId, performanceId, status);
    }

    @Override
    public Optional<Order> findByHoldKeyAndStatusForUpdate(
            final String holdKey, final OrderState status) {
        return jpaRepository.findByHoldKeyAndStatusForUpdate(holdKey, status);
    }

    @Override
    public Optional<Order> findByIdAndStatusForUpdate(final Long orderId, final OrderState status) {
        return jpaRepository.findByIdAndStatusForUpdate(orderId, status);
    }

    @Override
    public List<Order> findExpirable(
            final OrderState status,
            final LocalDateTime expiresAt,
            final Long afterOrderId,
            final int limit) {
        return jpaRepository
                .findAllByStatusAndExpiresAtLessThanEqualAndIdGreaterThan(
                        status,
                        expiresAt,
                        afterOrderId == null ? Long.MIN_VALUE : afterOrderId,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id")))
                .getContent();
    }
}
