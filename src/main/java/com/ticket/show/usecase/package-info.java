/**
 * show의 use case 조립과 트랜잭션 경계다.
 *
 * <p>공개 계약({@code show.api})의 구현과 요청 단위 use case가 함께 있다. 조회가 주고받는 타입도 여기 있다 — 응답 항목과 DB 집계 결과는 그 use case의 중첩 record가
 * 갖고({@code GetShowsUseCase.Item}, {@code GetShowDetailUseCase.PriceSummary}), 여러 use case가 함께 쓰는 조회
 * 파라미터·커서·정렬({@code ShowListParam}·{@code ShowSearchCriteria}·{@code SaleOpeningSoonSearchParam}·{@code ShowCursor}·{@code ShowSort})만
 * package 최상위에 둔다.
 */
@NullMarked
package com.ticket.show.usecase;

import org.jspecify.annotations.NullMarked;
