/**
 * identity의 Spring Security 배선(전역 {@code SecurityFilterChain}, OAuth2 handler, JWT 인증
 * argument resolver)이다.
 *
 * <p>{@code @NamedInterface("security")}는 {@code com.ticket.config.internal.WebConfig}가
 * {@link com.ticket.identity.internal.infrastructure.security.AuthenticatedMemberArgumentResolver}를
 * 등록하기 위해 이 package를 참조할 수 있게 연다({@code com.ticket.config}의 package-info 참고).
 * 이 package에는 {@code SecurityConfig} 등 identity 전용 구현도 함께 있어 NamedInterface가
 * 그것까지 노출한다 — {@code config}가 실제로 쓰는 건 argument resolver 하나뿐이지만, 이 하나만
 * 더 좁게 떼어내는 재구성은 하지 않았다(과한 조정으로 판단).
 */
@NamedInterface("security")
package com.ticket.identity.internal.infrastructure.security;

import org.springframework.modulith.NamedInterface;
