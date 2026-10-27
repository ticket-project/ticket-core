package com.ticket.booking.order.domain;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 주문 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>구현은 Spring Data JPA가 만든다. 비관적 락과 JPQL은 메서드의 {@code @Lock}/{@code @Query}에, 상태 고정 인자 같은 작은 변환은 {@code default} 메서드에
 * 둔다.
 */
public interface OrderRepository extends Repository<Order, Long> {
    <S extends Order> S save(S order);

    /** 잠금 없이 주문을 조회한다. 커밋 뒤 이벤트 listener가 현재 상태를 읽기 전용으로 다시 확인할 때 쓴다. */
    Optional<Order> findById(Long orderId);

    /** 주문을 잠근 뒤 반환한다. 상태 전이 전에 동시 갱신을 막기 위해 쓴다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.orderKey = :orderKey
              and o.memberId = :memberId
            """)
    Optional<Order> findByOrderKeyAndMemberIdForUpdate(
            @Param("orderKey") String orderKey, @Param("memberId") Long memberId);

    /** 잠금 없이 주문을 조회한다. 상태 조회처럼 읽기만 하는 경로가 쓴다 — 상태 전이 경로는 {@code *ForUpdate}를 쓴다. */
    Optional<Order> findByOrderKeyAndMemberId(String orderKey, Long memberId);

    /**
     * 좌석까지 한 번에 채워 주문을 조회한다. 주문 상세처럼 좌석을 모두 읽는 경로가 쓴다. {@code join fetch}라 쿼리는 한 개이고, 좌석 순서는 {@code Order.orderSeats}의
     * {@code @OrderBy("id ASC")}가 fetch join SQL에 그대로 붙어 보존된다.
     */
    @Query("""
            select o
            from Order o
            join fetch o.orderSeats
            where o.orderKey = :orderKey
              and o.memberId = :memberId
            """)
    Optional<Order> findDetailByOrderKeyAndMemberId(
            @Param("orderKey") String orderKey, @Param("memberId") Long memberId);

    default boolean existsPendingByMemberIdAndPerformanceId(final Long memberId, final Long performanceId) {
        return existsByMemberIdAndPerformanceIdAndStatus(memberId, performanceId, OrderState.PENDING);
    }

    boolean existsByMemberIdAndPerformanceIdAndStatus(Long memberId, Long performanceId, OrderState status);

    /** PENDING 주문만 잠근 뒤 반환한다. 만료 처리처럼 PENDING에서만 전이하는 경로가 쓴다. */
    default Optional<Order> findPendingByHoldKeyForUpdate(final String holdKey) {
        return findByHoldKeyAndStatusForUpdate(holdKey, OrderState.PENDING);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.holdKey = :holdKey
              and o.status = :status
            """)
    Optional<Order> findByHoldKeyAndStatusForUpdate(
            @Param("holdKey") String holdKey, @Param("status") OrderState status);

    /**
     * 만료 시각이 지난 PENDING 주문 중 {@code afterOrderId}보다 id가 큰 첫 주문을 잠근다. 다른 트랜잭션이 잠근 행은 기다리지 않고 건너뛴다({@code FOR UPDATE SKIP
     * LOCKED}) — 모든 인스턴스가 함께 청소해도 같은 주문을 두 번 처리하지 않고 서로 기다리지도 않는다.
     *
     * <p>{@code afterOrderId}는 같은 순회 안의 진행 보장이다. 계속 실패하는 주문은 롤백으로 잠금이 풀려 다시 첫 주문이 되므로, 커서로 지나가야 뒤의 주문이 처리된다.
     */
    default Optional<Order> findNextExpirableForUpdateSkipLocked(final LocalDateTime now, final long afterOrderId) {
        return findFirstByStatusAndExpiresAtLessThanEqualAndIdGreaterThanOrderByIdAsc(
                OrderState.PENDING, now, afterOrderId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    Optional<Order> findFirstByStatusAndExpiresAtLessThanEqualAndIdGreaterThanOrderByIdAsc(
            OrderState status, LocalDateTime expiresAt, Long afterOrderId);

    /** 같은 회원·회차의 PENDING 주문을 잠근다. 회원·회차당 PENDING은 하나다(uk_orders_pending_member_performance). */
    default Optional<Order> findPendingByMemberIdAndPerformanceIdForUpdate(
            final Long memberId, final Long performanceId) {
        return findByMemberIdAndPerformanceIdAndStatusForUpdate(memberId, performanceId, OrderState.PENDING);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.memberId = :memberId
              and o.performanceId = :performanceId
              and o.status = :status
            """)
    Optional<Order> findByMemberIdAndPerformanceIdAndStatusForUpdate(
            @Param("memberId") Long memberId,
            @Param("performanceId") Long performanceId,
            @Param("status") OrderState status);
}
