/**
 * 주문 HTTP 진입점이다. Controller와 요청 DTO를 둔다. OpenAPI 문서 interface는 {@code endpoint.docs}에 있다.
 *
 * <p>{@code HoldController}도 여기 있다 — 경로 이름은 hold지만 실제로 부르는 것은 {@code StartBookingUseCase}이고 결과물은 주문이다. HTTP 경로는 그대로
 * 유지한다.
 */
@NullMarked
package com.ticket.booking.order.endpoint;

import org.jspecify.annotations.NullMarked;
