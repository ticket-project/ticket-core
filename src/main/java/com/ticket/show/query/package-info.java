/**
 * show 조회가 주고받는 타입만 모은 package다. 조회 구현은 여기 없다 — {@code show.persistence}의 {@code
 * ShowQueryRepository}·{@code PerformanceQueryRepository}가 소유한다.
 *
 * <p>조회 실행 파라미터({@code *Param}·{@code *Criteria}·{@code ShowCursor}·{@code ShowSort})와 DB 집계
 * 결과({@code PriceSummary})가 있다. 조회는 엔티티로 충분하면 엔티티를 그대로 돌려주므로 값을 한 번 담았다 옮기기만 하는 중간 타입은 두지 않고, 응답
 * 항목은 각 use case의 중첩 record가 소유한다.
 *
 * <p>Querydsl 타입은 여기 들어오지 않는다. 정렬·커서·판매 상태 조건은 전부 조회 구현 안에 있다.
 */
@NullMarked
package com.ticket.show.query;

import org.jspecify.annotations.NullMarked;
