package com.ticket.core.infra.config;

import com.ticket.core.infra.auth.token.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 설정을 소유 모듈에서 등록한다. 토큰 구현이 core-infra로 내려오면서 설정도 함께 왔다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}
