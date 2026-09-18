/**
 * show의 local DB 조회 구현과 읽기 모델이다.
 *
 * <p>조회 구현({@code *Query}), 조회 실행 파라미터({@code *Param}·{@code *Criteria}·{@code ShowCursor}·{@code
 * ShowSort}), 조회가 돌려주는 projection({@code *Row}), use case가 응답용으로 조합한 결과({@code *View}·{@code
 * *Info})가 함께 있다. 조회와 그 조회가 주고받는 타입은 늘 함께 바뀌므로 나누지 않는다.
 *
 * <p>Querydsl 사용은 여기까지다. 정렬·커서·판매 상태 조건 helper({@code QuerydslShowSortResolver}, {@code
 * QuerydslShowCursorConditionBuilder}, {@code SaleDisplayStatusPredicates}, {@code
 * QuerydslTupleColumns})도 여기 함께 둔다 — package-private으로 유지하려면 쓰는 쪽과 같은 package에 있어야 한다.
 */
@NullMarked
package com.ticket.show.query;

import org.jspecify.annotations.NullMarked;
