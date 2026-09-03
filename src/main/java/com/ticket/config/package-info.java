/**
 * Config module: <b>앱에 적용되는 전역 배선</b>을 모은다.
 *
 * <p>어떤 business module도 참조하지 않는 domain-free 전역 기술 설정이 여기 있다 —
 * {@code SwaggerConfig}, {@code P6SpyConfig}, {@code QuerydslConfig}, {@code RedissonConfig},
 * {@code UuidSupplierConfig}, {@code EventPublicationMaintenance}, {@code SchedulingConfig},
 * {@code SystemClockConfig}. 이들은 module 결합이 없어 {@code shared}에 둘 수도 있어 보이지만,
 * bean을 등록한다는 점에서 계약과 성질이 다르고 {@code sharedModules} 선언 때문에 모든 module
 * 테스트에 함께 뜬다({@code com.ticket.shared}의 package-info 참고).
 *
 * <p>여기에 남은 유일한 module 참조는 {@link com.ticket.config.internal.JpaAuditingConfig}·
 * {@link com.ticket.config.internal.SecurityContextAuditorAware}다. JPA auditing이 채우는 감사자 id를
 * identity의 <b>공개 계약</b> {@link com.ticket.identity.AuthenticatedMember}에서 읽으므로
 * {@code allowedDependencies}에 {@code "identity"}만 있으면 되고, internal을 열 필요가 없다.
 *
 * <p><b>{@code @NamedInterface}로 열던 네 갈래는 사라졌다.</b> 예전에는 이 module이
 * {@code WebConfig}/{@code WebSocketConfig}/{@code HttpServiceConfig}/{@code JwtConfig}로
 * identity·booking의 물건을 대신 등록해 주면서 {@code identity :: security}·
 * {@code identity :: oauth2}·{@code identity :: token}·{@code booking :: websocket}을 참조해야 했다.
 * 등록을 소유 module로 옮겨 그 참조가 필요 없어졌다 —
 * {@code identity.internal.infrastructure.security.IdentityWebMvcConfig},
 * {@code identity.internal.infrastructure.auth.token.JwtConfig},
 * {@code identity.internal.infrastructure.auth.oauth2.HttpServiceConfig},
 * {@code booking.internal.infrastructure.websocket.WebSocketConfig}가 각자 자기 것을 등록한다.
 * Spring이 {@code WebMvcConfigurer}/{@code WebSocketMessageBrokerConfigurer} 구현을 여러 개 모아
 * 적용하므로 module마다 하나씩 둬도 되고, <b>새 module이 자기 확장점을 추가할 때 이 module을 고칠
 * 필요가 없다</b>.
 *
 * <p>이 module 자체는 다른 module이 참조하지 않는 leaf다 — composition root 역할이지 재사용 가능한
 * 공개 API가 아니다. {@code shared}와의 차이는 정확히 이 지점이다: {@code shared}는 다른 module이
 * <b>호출하는 계약</b>만 갖고 bean을 등록하지 않으며, {@code config}는 반대로 <b>적용되는 배선</b>이
 * 있을 자리다.
 *
 * <p>{@link com.ticket.shared.CorsProperties}는 여기 없다 — identity의 {@code SecurityConfig}와
 * booking의 {@code WebSocketConfig}가 함께 쓰는 값이라 {@code shared}에 둔다.
 * {@code @ConfigurationProperties} 값 홀더는 스스로 bean을 등록하지 않아 {@code shared}의 규칙에도
 * 어긋나지 않고, 등록은 그 값을 쓰는 두 module이 각자 {@code @EnableConfigurationProperties}로 한다.
 */
@ApplicationModule(displayName = "Config", allowedDependencies = {"identity"})
package com.ticket.config;

import org.springframework.modulith.ApplicationModule;
