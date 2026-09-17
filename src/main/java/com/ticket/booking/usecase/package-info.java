/**
 * 여러 capability를 동시에 조율하는 booking 전체 workflow다.
 *
 * <p>{@code StartBookingUseCase}는 판매 정책·대기열 입장·회원·좌석 가용성·선점·주문·보상을 한 흐름에서 조율한다 — 어느 한 capability의
 * use case가 아니므로 특정 capability에 넣지 않는다. {@code GetShowSeatMapUseCase}도 show 공개 계약만 부르는 booking
 * 진입점이라 여기 둔다.
 *
 * <p>한 capability 안에서 끝나는 use case는 그 capability가 소유한다({@code booking.order.usecase} 등).
 */
@NullMarked
package com.ticket.booking.usecase;

import org.jspecify.annotations.NullMarked;
