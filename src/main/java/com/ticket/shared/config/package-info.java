/**
 * 둘 이상의 module이 함께 읽는 설정 값 홀더다.
 *
 * <p>공개면: {@link com.ticket.shared.config.CorsProperties}(CORS 허용 origin). security의 {@code ApiSecurityConfig}와
 * booking의 {@code WebSocketConfig}가 함께 읽는다.
 *
 * <p>{@code shared.api}가 아닌 이유는 {@code @ConfigurationProperties}가 Spring Boot 설정 바인딩 기술이기 때문이다 — {@code shared.api}는 기술
 * 중립 계약만 갖는다. 값 홀더는 스스로 bean을 등록하지 않고, 등록은 그 값을 쓰는 module이 {@code @EnableConfigurationProperties}로 한다.
 */
@NullMarked
@org.springframework.modulith.NamedInterface("config")
package com.ticket.shared.config;

import org.jspecify.annotations.NullMarked;
