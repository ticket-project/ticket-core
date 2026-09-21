/**
 * show의 저장 adapter와 local DB 조회 Repository 구현이다.
 *
 * <p>{@code *RepositoryAdapter}가 show Aggregate 저장 계약({@code *Repository})을 구현하고, 그 안에서 쓰는 {@code
 * SpringData*JpaRepository}는 package-private으로 같이 둔다 — Spring Data 인터페이스는 어댑터의 구현 수단이지 밖에서 부르는 계약이
 * 아니다.
 *
 * <p>읽기 전용 조회는 {@code ShowQueryRepository}(공연 목록·검색·오픈 예정·상세·요약 배치)와 {@code
 * PerformanceRepository}(회차 요약·grade·판매 snapshot·seat-map)가 소유한다. 정렬·커서·판매 표시 상태 조건 같은 Querydsl 조립은
 * {@code ShowQueryRepository} 안에만 있고, 고정 projection 조회는 {@code SpringData*JpaRepository}의
 * {@code @Query}가 갖는다({@code docs/readability-guidelines.md} §10). 밖으로는 entity와 DB 집계 결과만 나간다 — 응답
 * 항목은 그것을 쓰는 use case가 소유한다({@code show.usecase}).
 */
@NullMarked
package com.ticket.show.persistence;

import org.jspecify.annotations.NullMarked;
