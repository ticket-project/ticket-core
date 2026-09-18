/**
 * show 조회가 주고받는 타입만 모은 package다. 조회 구현은 여기 없다 — {@code show.persistence}의 {@code
 * ShowQueryRepository}·{@code PerformanceQueryRepository}가 소유한다.
 *
 * <p>조회 실행 파라미터({@code *Param}·{@code *Criteria}·{@code ShowCursor}·{@code ShowSort})와 조회가 돌려주는
 * projection({@code *Row}·{@code *Info})이 있다. 응답으로 나가는 조합 결과({@code *View})는 {@code
 * show.usecase.view}가 갖는다 — 조회 결과와 응답은 모양이 다르다({@code *Row}는 {@code venueId} scalar만 담고, 공연장 이름·지역은
 * use case가 채운다).
 *
 * <p>Querydsl 타입은 여기 들어오지 않는다. 정렬·커서·판매 상태 조건은 전부 조회 구현 안에 있다.
 */
@NullMarked
package com.ticket.show.query;

import org.jspecify.annotations.NullMarked;
