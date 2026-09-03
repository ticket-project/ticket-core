/**
 * booking의 WebSocket STOMP 인증 인터셉터다.
 *
 * <p>{@code @NamedInterface("websocket")}은 {@code com.ticket.config.internal.WebSocketConfig}가
 * {@link com.ticket.booking.internal.infrastructure.websocket.WebSocketAuthInterceptor}를 STOMP
 * 인바운드 채널에 등록하기 위해 이 package를 참조할 수 있게 연다({@code com.ticket.config}의
 * package-info 참고). 이 package에는 이 클래스 하나만 있어 노출 범위가 정확히 필요한 만큼이다.
 */
@NamedInterface("websocket")
package com.ticket.booking.internal.infrastructure.websocket;

import org.springframework.modulith.NamedInterface;
