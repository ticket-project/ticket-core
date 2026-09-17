/**
 * show 조회 경계의 계약과 읽기 모델이다.
 *
 * <p>조회 계약({@code *QueryPort}), 조회 실행 파라미터({@code *Param}·{@code *Criteria}·{@code
 * ShowCursor}·{@code ShowSort}), port가 돌려주는 projection({@code *Row}), use case가 응답용으로 조합한 결과({@code
 * *View}·{@code *Info})가 함께 있다. 계약과 그 계약이 주고받는 타입은 늘 함께 바뀌므로 나누지 않는다.
 *
 * <p>구현은 {@code show.persistence.querydsl}이 갖는다 — 계약과 구현을 다른 package에 두어야 use case가 Querydsl 어댑터를
 * 직접 부르는 것을 규칙으로 막을 수 있다.
 */
@NullMarked
package com.ticket.show.query;

import org.jspecify.annotations.NullMarked;
