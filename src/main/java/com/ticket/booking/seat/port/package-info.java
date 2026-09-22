/**
 * 좌석 상태 변화를 밖으로 알리는 계약과 그 payload다.
 *
 * <p>구독자에게 실제로 보내는 일은 {@code booking.websocket}이 맡는다 — 계약을 구현과 다른 package에 두어야 좌석 use case가 STOMP 브로커를 직접 알지 않는다.
 */
@NullMarked
package com.ticket.booking.seat.port;

import org.jspecify.annotations.NullMarked;
