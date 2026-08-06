package com.ticket.core.domain.order.repository;

import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderKeyAndMemberId(String orderKey, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.orderKey = :orderKey
              and o.memberId = :memberId
            """)
    Optional<Order> findByOrderKeyAndMemberIdForUpdate(
            @Param("orderKey") String orderKey,
            @Param("memberId") Long memberId
    );

    boolean existsByMemberIdAndPerformanceIdAndStatus(Long memberId, Long performanceId, OrderState status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.holdKey = :holdKey
              and o.status = :status
            """)
    Optional<Order> findByHoldKeyAndStatusForUpdate(
            @Param("holdKey") String holdKey,
            @Param("status") OrderState status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.id = :orderId
              and o.status = :status
            """)
    Optional<Order> findByIdAndStatusForUpdate(
            @Param("orderId") Long orderId,
            @Param("status") OrderState status
    );

    Slice<Order> findAllByStatusAndExpiresAtLessThanEqual(OrderState status, LocalDateTime expiresAt, Pageable pageable);
}
