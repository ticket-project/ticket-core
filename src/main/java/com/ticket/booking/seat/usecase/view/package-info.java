/**
 * use case가 응답으로 내보내는 조합 결과({@code *View})만 모은 package다.
 *
 * <p>{@code SeatStateView}는 {@code GetSeatStatusUseCase.Output}의 목록 항목으로 그대로 직렬화되는 최종 응답 모양이다. 조회
 * projection은 {@code booking.seat.query}와 {@code booking.seat.persistence}에 남는다.
 */
@NullMarked
package com.ticket.booking.seat.usecase.view;

import org.jspecify.annotations.NullMarked;
