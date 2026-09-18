/**
 * show의 use case 조립과 트랜잭션 경계다.
 *
 * <p>공개 계약({@code show.api})의 구현과 요청 단위 use case가 함께 있다. 응답 항목은 그 use case의 중첩 record가 갖고({@code
 * GetShowsUseCase.Item}), 조회 파라미터와 DB 집계 결과({@code *Param}·{@code *Criteria}·{@code PriceSummary})는
 * {@code show.query}에 따로 둔다.
 */
@NullMarked
package com.ticket.show.usecase;

import org.jspecify.annotations.NullMarked;
