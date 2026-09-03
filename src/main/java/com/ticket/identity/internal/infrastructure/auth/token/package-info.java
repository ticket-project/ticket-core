/**
 * identity의 access/refresh token 구현(JWT 발급·검증, Redis refresh token 저장소, 설정)이다.
 *
 * <p>{@code @NamedInterface("token")}은 {@code com.ticket.config.internal.JwtConfig}가
 * {@link com.ticket.identity.internal.infrastructure.auth.token.JwtProperties}를
 * {@code @EnableConfigurationProperties}로 등록하기 위해 이 package를 참조할 수 있게 연다
 * ({@code com.ticket.config}의 package-info 참고). 이 package의 다른 구현({@code JwtAccessTokenCodec},
 * {@code JwtAuthTokenIssuer}, {@code RedisRefreshTokenStore})도 함께 노출되지만, {@code config}가
 * 실제로 쓰는 건 {@code JwtProperties} 하나뿐이다 — 이 하나만 더 좁게 떼어내는 재구성은 하지
 * 않았다(과한 조정으로 판단).
 */
@NamedInterface("token")
package com.ticket.identity.internal.infrastructure.auth.token;

import org.springframework.modulith.NamedInterface;
