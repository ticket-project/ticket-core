/**
 * show Aggregate 저장 계약({@code *Repository})의 구현이다.
 *
 * <p>{@code *RepositoryAdapter}가 domain 계약을 구현하고, 그 안에서 쓰는 {@code SpringData*JpaRepository}는
 * package-private으로 같이 둔다 — Spring Data 인터페이스는 어댑터의 구현 수단이지 밖에서 부르는 계약이 아니다.
 */
@NullMarked
package com.ticket.show.persistence;

import org.jspecify.annotations.NullMarked;
