/**
 * 앱 전반에 적용되는 기술 설정과, 둘 이상의 module이 함께 읽는 설정 값을 모은다.
 *
 * <p><b>이 package 자체는 공개면이 아니다.</b> {@code @NamedInterface}를 package가 아니라
 * {@link com.ticket.shared.config.CorsProperties} 타입 하나에만 붙인다 — 아래 전역 설정들은 shared 내부 배선이라 다른 module이 참조할 이유가 없다.
 *
 * <p>어떤 business module도 참조하지 않는 domain-free 전역 기술 설정: {@code SwaggerConfig}, {@code P6SpyConfig},
 * {@code QuerydslConfig}, {@code RedissonConfig}, {@code EventPublicationMaintenance}, {@code SchedulingConfig},
 * {@code SystemClockConfig}. 각 module 자신의 확장점(예: {@code WebMvcConfigurer})은 그 module이 직접 등록한다 — 여기서 대신 등록하지 않는다.
 *
 * <p>JPA auditing은 여기서 활성화하고, 감사자 ID를 읽는 {@code AuditorAware} 구현({@code SecurityContextAuditorAware})도 여기 있다. shared가
 * member를 참조하지 않는 것은 인증 주체가 shared의 {@code AuditorPrincipal} SPI를 <b>구현</b>하기 때문이다 — 참조 방향이 반대라 순환이 생기지 않는다.
 *
 * <p>{@code CorsProperties}만 공개하는 이유는 security의 {@code ApiSecurityConfig}(HTTP CORS)와 booking의
 * {@code WebSocketConfig}(STOMP 핸드셰이크 허용 origin)가 <b>같은 값</b>을 써야 하기 때문이다. 어긋나면 API는 되는데 WebSocket만 막힌다. 값 홀더는 스스로
 * bean을 등록하지 않고, 등록은 그 값을 쓰는 module이 {@code @EnableConfigurationProperties}로 한다.
 */
@NullMarked
package com.ticket.shared.config;

import org.jspecify.annotations.NullMarked;
