package com.ticket.identity.internal.infrastructure.auth.token;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 설정을 소유 module이 직접 등록한다.
 *
 * <p>{@link JwtProperties}는 identity의 token 구현이므로 등록도 여기서 한다 — 전역 설정 module이
 * identity의 {@code internal}을 열어 보고 등록해 주면 그 방향의 참조를 열기 위해
 * {@code @NamedInterface}가 필요해진다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {
}
