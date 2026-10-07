package com.ticket.booking.order.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 주문 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>구현은 Spring Data JPA가 만든다. 비관적 락과 JPQL은 메서드의 {@code @Lock}/{@code @Query}에, 상태 고정 인자와 페이징 같은 작은 변환은 {@code default}
 * 메서드에 둔다.
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

    default Optional<Order> findPendingByIdForUpdate(final Long orderId) {
        return findByIdAndStatusForUpdate(orderId, OrderState.PENDING);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o
            from Order o
            where o.id = :orderId
              and o.status = :status
            """)
    Optional<Order> findByIdAndStatusForUpdate(@Param("orderId") Long orderId, @Param("status") OrderState status);

    /**
     * 만료 시각이 지난 PENDING 주문을 id 오름차순으로 최대 {@code limit}건 조회한다. {@code afterOrderId}보다 큰 id만 돌려주는 커서 조회다.
     *
     * <p>커서를 두는 이유는 진행 보장이다 — 앞쪽 주문이 계속 실패해도 다음 페이지로 넘어가야 뒤의 정상 대상이 처리된다. id는 불변이고 유일하므로 안정적인 커서가 된다. 첫 페이지는
     * {@code afterOrderId}에 {@code null}을 넘긴다.
     *
     * <p>반환 건수가 {@code limit}보다 적으면 더 처리할 대상이 없다는 뜻이다.
     */
    default List<Order> findExpirable(
            final LocalDateTime expiresAt, final @Nullable Long afterOrderId, final int limit) {
        return findAllByStatusAndExpiresAtLessThanEqualAndIdGreaterThan(
                        OrderState.PENDING,
                        expiresAt,
                        afterOrderId == null ? Long.MIN_VALUE : afterOrderId,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "id")))
                .getContent();
    }

    Slice<Order> findAllByStatusAndExpiresAtLessThanEqualAndIdGreaterThan(
            OrderState status, LocalDateTime expiresAt, Long afterOrderId, Pageable pageable);
}
