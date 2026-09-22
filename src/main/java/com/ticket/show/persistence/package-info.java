/**
 * show의 저장 adapter와 local DB 조회 Repository 구현이다.
 *
 * <p>{@code *RepositoryAdapter}가 show Aggregate 저장 계약({@code *Repository})을 구현하고, 그 안에서 쓰는
 * {@code SpringData*JpaRepository}는 package-private으로 같이 둔다 — Spring Data 인터페이스는 어댑터의 구현 수단이지 밖에서 부르는 계약이 아니다.
 *
 * <p>동적 조건·커서 페이징·집계가 필요한 조회는 {@code ShowQuerydslRepository}가 Querydsl로 갖는다(공연 목록·검색·오픈 예정·가격 집계). 고정 조회는
 * 계약({@code *Repository})과 그 adapter로 내려가 있다. 밖으로 나가는 것은 entity와 DB 집계 결과뿐이고, 응답 항목은 그것을 쓰는 use case가
 * 소유한다({@code show.usecase}).
 */
@NullMarked
package com.ticket.show.persistence;

import org.jspecify.annotations.NullMarked;
