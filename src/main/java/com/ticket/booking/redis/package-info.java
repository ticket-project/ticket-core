/**
 * Redis 키 만료 알림을 받아 등록된 handler로 넘기는 배선이다.
 *
 * <p>만료를 실제로 처리하는 handler는 그 상태를 소유한 capability가 갖는다({@code
 * booking.hold.persistence.HoldKeyExpirationHandler}, {@code
 * booking.selection.persistence.SeatSelectionExpirationHandler}). 여기 있는 것은 어느 capability에도 속하지 않는
 * 수신 배선뿐이다.
 */
@NullMarked
package com.ticket.booking.redis;

import org.jspecify.annotations.NullMarked;
