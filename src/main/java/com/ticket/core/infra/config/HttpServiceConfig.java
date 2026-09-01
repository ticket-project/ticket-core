package com.ticket.core.infra.config;

import com.ticket.core.infra.auth.oauth2.KakaoUnlinkApiClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 외부 HTTP 서비스 클라이언트 등록
 * Spring Framework 7 / Spring Boot 4의 @ImportHttpServices 활용
 *
 * base-url, timeout 등은 core-api 의 application.yml 에서
 * spring.http.serviceclient.{group} 프로퍼티로 자동 설정된다.
 */
@Configuration
@ImportHttpServices(group = "kakao", types = KakaoUnlinkApiClient.class)
public class HttpServiceConfig {
}
