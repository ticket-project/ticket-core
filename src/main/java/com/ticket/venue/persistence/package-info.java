/**
 * venue의 JPA 저장·조회 구현이다.
 *
 * <p>{@code *RepositoryAdapter}가 Aggregate 복원 계약({@code venue.domain}의 {@code *Repository})을 구현하고,
 * 그 안에서 쓰는 {@code SpringData*JpaRepository}는 package-private으로 같이 둔다 — Spring Data 인터페이스는 어댑터의 구현
 * 수단이지 밖에서 부르는 계약이 아니다.
 *
 * <p>조회는 엔티티를 반환한다. 공개 계약({@code venue.api}) 타입으로의 변환은 {@code venue.usecase}가 한다({@code
 * docs/readability-guidelines.md} §10-1).
 */
@NullMarked
package com.ticket.venue.persistence;

import org.jspecify.annotations.NullMarked;
