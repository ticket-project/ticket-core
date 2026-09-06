package com.ticket.member.infrastructure.auth.oauth2;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 외부 HTTP 서비스 클라이언트를 소유 module이 직접 등록한다.
 *
 * <p>Spring Framework 7 / Spring Boot 4의 {@code @ImportHttpServices}로 {@link KakaoUnlinkApiClient}를
 * 등록한다. base-url, timeout 등은 {@code application.yml}의
 * {@code spring.http.serviceclient.kakao} 프로퍼티로 자동 설정된다.
 *
 * <p>카카오 연동은 member 소유이므로 등록도 여기서 한다 — 전역 설정 module이 member의
 * 하위 package를 열어 보고 등록해 주면 그 방향의 참조를 열기 위해 {@code @NamedInterface}가
 * 필요해진다.
 */
@Configuration
@ImportHttpServices(group = "kakao", types = KakaoUnlinkApiClient.class)
class HttpServiceConfig {
}
