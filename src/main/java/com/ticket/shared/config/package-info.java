/**
 * 앱 전반에 적용되는 기술 설정을 모은다.
 *
 * <p>어떤 business module도 참조하지 않는 domain-free 전역 기술 설정이 여기 있다 —
 * {@code SwaggerConfig}, {@code P6SpyConfig}, {@code QuerydslConfig}, {@code RedissonConfig},
 * {@code EventPublicationMaintenance}, {@code SchedulingConfig},
 * {@code SystemClockConfig}. 각 module 자신의 확장점(예: {@code WebMvcConfigurer})은 그 module이
 * 직접 등록한다 — 이 module은 그것들을 대신 등록하지 않는다.
 *
 * <p>JPA auditing은 여기서 활성화하고, 감사자 ID를 읽는 {@code AuditorAware} 빈은 인증 주체를 소유한
 * member 모듈이 제공한다. shared가 member를 참조하지 않아 순환 의존이 생기지 않는다.
 *
 * <p>{@link com.ticket.shared.CorsProperties}는 여기 없다 — member의 {@code SecurityConfig}와
 * booking의 {@code WebSocketConfig}가 함께 쓰는 값이라 {@code shared}에 둔다.
 */
package com.ticket.shared.config;
