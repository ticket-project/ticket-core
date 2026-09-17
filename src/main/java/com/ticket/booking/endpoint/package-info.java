/**
 * 여러 capability를 조율하는 booking workflow의 HTTP 진입점이다.
 *
 * <p>{@code HoldController}와 {@code ShowSeatMapController}는 {@code booking.usecase}의 module 전체
 * workflow를 부른다. 한 capability에 명확히 속하는 Controller는 그 capability가 소유한다({@code
 * booking.order.endpoint} 등).
 */
@NullMarked
package com.ticket.booking.endpoint;

import org.jspecify.annotations.NullMarked;
