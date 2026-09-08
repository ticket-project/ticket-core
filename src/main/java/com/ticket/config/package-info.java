/**
 * Config BC: <b>앱에 적용되는 전역 배선</b>을 모은다.
 *
 * <p>어떤 business module도 참조하지 않는 domain-free 전역 기술 설정이 여기 있다 —
 * {@code SwaggerConfig}, {@code P6SpyConfig}, {@code QuerydslConfig}, {@code RedissonConfig},
 * {@code UuidSupplierConfig}, {@code EventPublicationMaintenance}, {@code SchedulingConfig},
 * {@code SystemClockConfig}. 각 module 자신의 확장점(예: {@code WebMvcConfigurer})은 그 module이
 * 직접 등록한다 — 이 module은 그것들을 대신 등록하지 않는다.
 *
 * <p>여기 남은 유일한 module 참조는 {@link com.ticket.config.JpaAuditingConfig}·
 * {@link com.ticket.config.SecurityContextAuditorAware}다. JPA auditing이 채우는 감사자 id를
 * member의 <b>공개 계약</b> {@link com.ticket.member.AuthenticatedMember}에서 읽으므로
 * {@code allowedDependencies}에 {@code "member"}만 있으면 되고, 내부를 열 필요가 없다.
 *
 * <p>이 module 자체는 다른 module이 참조하지 않는 leaf다 — composition root 역할이지 재사용 가능한
 * 공개 API가 아니다. {@code shared}와의 차이는 정확히 이 지점이다: {@code shared}는 다른 module이
 * <b>호출하는 계약</b>만 갖고 bean을 등록하지 않으며, {@code config}는 반대로 <b>적용되는 배선</b>이
 * 있을 자리다.
 *
 * <p>{@link com.ticket.shared.CorsProperties}는 여기 없다 — member의 {@code SecurityConfig}와
 * booking의 {@code WebSocketConfig}가 함께 쓰는 값이라 {@code shared}에 둔다.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Config", allowedDependencies = {"member"})
package com.ticket.config;
