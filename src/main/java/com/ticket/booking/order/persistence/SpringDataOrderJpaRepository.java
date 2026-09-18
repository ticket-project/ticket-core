package com.ticket.booking.order.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderState;

interface SpringDataOrderJpaRepository extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select o
            from Order o
            where o.orderKey = :orderKey
              and o.memberId = :memberId
            """)
    Optional<Order> findByOrderKeyAndMemberIdForUpdate(
            @Param("orderKey") String orderKey, @Param("memberId") Long memberId);

    /**
     * 주문 상세에 필요한 좌석까지 한 번에 읽는다. {@code join fetch}라 쿼리는 한 개이고, 좌석 순서는 {@code Order.orderSeats}의
     * {@code @OrderBy("id ASC")}가 fetch join SQL에 그대로 붙어 보존된다.
     */
    @Query(
            """
            select o
            from Order o
            join fetch o.orderSeats
            where o.orderKey = :orderKey
              and o.memberId = :memberId
            """)
    Optional<Order> findDetailByOrderKeyAndMemberId(
            @Param("orderKey") String orderKey, @Param("memberId") Long memberId);

    /** 상태 조회 전용이라 락을 걸지 않는다 — 상태 전이 경로는 {@code *ForUpdate}를 쓴다. */
    Optional<Order> findByOrderKeyAndMemberId(String orderKey, Long memberId);

    boolean existsByMemberIdAndPerformanceIdAndStatus(
            Long memberId, Long performanceId, OrderState status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select o
            from Order o
            where o.holdKey = :holdKey
              and o.status = :status
            """)
    Optional<Order> findByHoldKeyAndStatusForUpdate(
            @Param("holdKey") String holdKey, @Param("status") OrderState status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select o
            from Order o
            where o.id = :orderId
              and o.status = :status
            """)
    Optional<Order> findByIdAndStatusForUpdate(
            @Param("orderId") Long orderId, @Param("status") OrderState status);

    Slice<Order> findAllByStatusAndExpiresAtLessThanEqualAndIdGreaterThan(
            OrderState status, LocalDateTime expiresAt, Long afterOrderId, Pageable pageable);
}
