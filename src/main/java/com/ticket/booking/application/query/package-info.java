/**
 * booking 조회 경계의 읽기 모델이다.
 *
 * <p>조회 port가 돌려주는 projection({@code *Row})과 use case가 응답용으로 조합한 결과({@code *View}), 그리고 그 결과가 쓰는
 * 표현용 열거({@link com.ticket.booking.seat.query.SeatStatus})가 모인다. 좌석의 도메인 lifecycle은 {@code
 * booking.domain.seat.PerformanceSeatState}이고 여기 {@code SeatStatus}는 그것을 조회 응답용으로 단순화한 표현이다 — 둘을 같은
 * 것으로 다루지 않는다.
 */
@NullMarked
package com.ticket.booking.application.query;

import org.jspecify.annotations.NullMarked;
