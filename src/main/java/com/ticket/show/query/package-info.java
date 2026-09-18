/**
 * show 조회가 주고받는 타입만 모은 package다. 조회 구현은 여기 없다 — {@code show.persistence}의 {@code
 * ShowQueryRepository}·{@code PerformanceQueryRepository}가 소유한다.
 *
 * <p>조회 실행 파라미터({@code *Param}·{@code *Criteria}·{@code ShowCursor}·{@code ShowSort}), 조회가 돌려주는
 * projection({@code *Row}), use case가 응답용으로 조합한 결과({@code *View}·{@code *Info})가 함께 있다. 이 타입들은 늘 함께
 * 바뀌므로 나누지 않는다.
 *
 * <p>Querydsl 타입은 여기 들어오지 않는다. 정렬·커서·판매 상태 조건은 전부 조회 구현 안에 있다.
 */
@NullMarked
package com.ticket.show.query;

import org.jspecify.annotations.NullMarked;
