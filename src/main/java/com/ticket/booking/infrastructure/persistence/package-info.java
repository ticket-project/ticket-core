/**
 * booking Aggregate 저장 계약({@code *Repository})의 구현이다.
 *
 * <p>{@code *RepositoryAdapter}가 domain 계약을 구현하고, 그 안에서 쓰는 {@code SpringData*JpaRepository}는
 * package-private으로 같이 둔다. hold 해제 완료 기록({@link
 * com.ticket.booking.infrastructure.persistence.HoldReleaseProgress})은 domain 모델이 아니라 재시도 멱등성을 위한
 * 저장 전용 entity라 domain이 아니라 여기 산다.
 */
@NullMarked
package com.ticket.booking.infrastructure.persistence;

import org.jspecify.annotations.NullMarked;
