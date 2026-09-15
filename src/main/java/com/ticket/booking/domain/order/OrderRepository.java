package com.ticket.booking.domain.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * 주문 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>계약에는 도메인 타입과 Java 기본 타입만 노출한다. 비관적 락, JPQL, 페이징 같은 기술은 {@code booking.order.infrastructure}의
 * {@code OrderRepositoryAdapter}가 결정한다.
 */
public interface OrderRepository {
    Order save(Order order);

    /** 잠금 없이 주문을 조회한다. 커밋 뒤 이벤트 listener가 현재 상태를 읽기 전용으로 다시 확인할 때 쓴다. */
    Optional<Order> findById(Long orderId);

    /** 주문을 잠근 뒤 반환한다. 상태 전이 전에 동시 갱신을 막기 위해 쓴다. */
    Optional<Order> findByOrderKeyAndMemberIdForUpdate(String orderKey, Long memberId);

    boolean existsByMemberIdAndPerformanceIdAndStatus(
            Long memberId, Long performanceId, OrderState status);

    Optional<Order> findByHoldKeyAndStatusForUpdate(String holdKey, OrderState status);

    Optional<Order> findByIdAndStatusForUpdate(Long orderId, OrderState status);

    /**
     * 만료 시각이 지난 주문을 id 오름차순으로 최대 {@code limit}건 조회한다. {@code afterOrderId}보다 큰 id만 돌려주는 커서 조회다.
     *
     * <p>커서를 두는 이유는 진행 보장이다 — 앞쪽 주문이 계속 실패해도 다음 페이지로 넘어가야 뒤의 정상 대상이 처리된다. id는 불변이고 유일하므로 안정적인 커서가
     * 된다. 첫 페이지는 {@code afterOrderId}에 {@code null}을 넘긴다.
     *
     * <p>반환 건수가 {@code limit}보다 적으면 더 처리할 대상이 없다는 뜻이다.
     */
    List<Order> findExpirable(
            OrderState status, LocalDateTime expiresAt, @Nullable Long afterOrderId, int limit);
}
