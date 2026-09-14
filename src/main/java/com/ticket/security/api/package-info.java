/**
 * security가 다른 module에 공개하는 계약이다.
 *
 * <p>{@link com.ticket.security.api.AccessTokenAuthenticator} 하나뿐이다 — HTTP filter chain을 타지 않는 경로
 * (booking의 WebSocket STOMP CONNECT)가 원본 access token 문자열을 검증할 때 쓴다. HTTP 경로의 만료/무효 구분은 {@code
 * security.token}의 읽기 계약이 보존하고, WebSocket은 그 구분이 필요 없어 하나의 인증 실패로 다룬다.
 *
 * <p><b>{@code Api} 접미사를 붙이지 않는다.</b> {@code -Authenticator}가 이미 "이것을 불러 인증한다"를 도메인 언어로 말한다 — 접미사를
 * 더해도 읽는 사람이 얻는 것이 없다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("api")
package com.ticket.security.api;

import org.jspecify.annotations.NullMarked;
