/**
 * Config module: <b>앱에 적용되는 전역 배선</b>을 모은다. 두 갈래가 있다.
 *
 * <ul>
 *   <li>여러 business module의 internal을 동시에 참조해야만 배선할 수 있는 설정 —
 *   {@link com.ticket.config.internal.WebConfig}, {@link com.ticket.config.internal.WebSocketConfig},
 *   {@link com.ticket.config.internal.HttpServiceConfig}, {@link com.ticket.config.internal.JwtConfig},
 *   {@link com.ticket.config.internal.JpaAuditingConfig}·
 *   {@link com.ticket.config.internal.SecurityContextAuditorAware}.</li>
 *   <li>어떤 business module도 참조하지 않는 domain-free 전역 기술 설정 —
 *   {@code SwaggerConfig}, {@code P6SpyConfig}, {@code QuerydslConfig}, {@code RedissonConfig},
 *   {@code UuidSupplierConfig}, {@code EventPublicationMaintenance}, {@code SchedulingConfig},
 *   {@code SystemClockConfig}. 이들은 module 결합이 없어 {@code shared}에 둘 수도 있어 보이지만,
 *   bean을 등록한다는 점에서 계약과 성질이 다르고 {@code sharedModules} 선언 때문에 모든 module
 *   테스트에 함께 뜬다({@code com.ticket.shared}의 package-info 참고).</li>
 * </ul>
 *
 * <p>이 module 자체는 어떤 module도 참조하지 않는 leaf({@code identity}/{@code booking} 쪽에서
 * 이 module로 들어오는 참조는 없다) — composition root 역할이지 재사용 가능한 공개 API가 아니므로
 * 다른 module이 {@code com.ticket.config}를 참조할 일이 없다. {@code shared}와의 차이는 정확히 이
 * 지점이다: {@code shared}는 다른 module이 <b>호출하는 계약</b>만 갖고 bean을 등록하지 않으며,
 * {@code config}는 반대로 <b>적용되는 배선</b>이 있을 자리다.
 *
 * <p>{@code identity}/{@code booking}의 internal 캡슐화를 깨지 않기 위해 필요한 만큼만
 * {@code @NamedInterface}로 열어 그 이름을 {@code allowedDependencies}에 명시한다 — 전체 module을
 * 여는 대신, 이 module이 실제로 쓰는 package만 노출한다.
 *
 * <ul>
 *   <li>{@code identity :: security} — {@code identity.internal.infrastructure.security}.
 *   {@link com.ticket.config.internal.WebConfig}가 {@code AuthenticatedMemberArgumentResolver}를
 *   등록하는 데 쓴다. 이 package에는 identity의 Spring Security filter chain 구현도 함께 있어
 *   NamedInterface가 그것까지 노출한다 — 딱 이 타입 하나만 더 좁게 떼어내는 재구성은 하지 않았다.</li>
 *   <li>{@code identity :: oauth2} — {@code identity.internal.infrastructure.auth.oauth2}.
 *   {@link com.ticket.config.internal.HttpServiceConfig}가 {@code KakaoUnlinkApiClient}를
 *   {@code @ImportHttpServices}로 등록하는 데 쓴다.</li>
 *   <li>{@code identity :: token} — {@code identity.internal.infrastructure.auth.token}.
 *   {@link com.ticket.config.internal.JwtConfig}가 {@code JwtProperties}를
 *   {@code @EnableConfigurationProperties}로 등록하는 데 쓴다.</li>
 *   <li>{@code identity} — identity의 공개 계약({@link com.ticket.identity.AuthenticatedMember}).
 *   {@link com.ticket.config.internal.SecurityContextAuditorAware}가 감사자 id를 채우는 데 쓴다.
 *   internal이 아니라 module root의 공개 API라 NamedInterface 없이 일반 module 의존으로 충분하다.</li>
 *   <li>{@code booking :: websocket} — {@code booking.internal.infrastructure.websocket}.
 *   {@link com.ticket.config.internal.WebSocketConfig}가 {@code WebSocketAuthInterceptor}를
 *   STOMP 인바운드 채널에 등록하는 데 쓴다.</li>
 * </ul>
 *
 * <p>{@code CorsProperties}는 여기 없다 — identity의 {@code SecurityConfig}도 CORS 설정을 위해
 * 직접 참조하므로(이 module만의 전유물이 아니므로) {@link com.ticket.shared.CorsProperties}에 둔다.
 * {@code @ConfigurationProperties} 값 홀더는 스스로 bean을 등록하지 않아 {@code shared}의 규칙에도
 * 어긋나지 않는다. 여기 두면 identity → config, config → identity(위 {@code identity :: security}
 * 등) 양방향 참조로 순환이 생긴다.
 */
@ApplicationModule(
        displayName = "Config",
        allowedDependencies = {"identity :: security", "identity :: oauth2", "identity :: token", "identity", "booking :: websocket"}
)
package com.ticket.config;

import org.springframework.modulith.ApplicationModule;
