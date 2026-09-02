package com.ticket.identity;

import com.ticket.core.infra.support.UuidSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.util.UUID;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다. 기본값(true)으로
 * 두면 이 STANDALONE 테스트가 별도로 {@code verify()}를 실행하는데, identity가 의존하는
 * {@code com.ticket.core.support.exception}·{@code com.ticket.core.config.security.CorsProperties}가
 * legacy {@code com.ticket.core} 아래에 있고, WebSocket 인증({@code WebSocketAuthInterceptor})과
 * legacy {@code order}·{@code showlike}·{@code performanceseat}·{@code WebConfig}가 아직 이동하지
 * 않아 identity internal({@code AccessTokenReader}, {@code Member}, {@code MemberRepository},
 * {@code AuthenticatedMemberArgumentResolver})을 그대로 참조해 "identity → core"·"core → identity"
 * 순환으로 오탐된다. {@code spring.modulith.detection-strategy}를 전역으로 바꾸는 대신 이 테스트에서만
 * 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지 않은 미래 모듈이 조용히 검증에서 빠지는
 * 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.identity} package tree만 component-scan한다.
 * {@code UuidSupplier} bean은 legacy {@code com.ticket.core.infra.config.UuidSupplierConfig}에 있어
 * 그 필터 밖이므로, {@code RedisOAuth2AuthCodeStore} 같은 identity 어댑터가 필요로 하는 bean만 이
 * 테스트 안에서 직접 채운다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
@Import(IdentityModuleTests.TestSupportConfig.class)
class IdentityModuleTests {

    @Test
    void bootstraps() {
    }

    @Configuration
    static class TestSupportConfig {

        @Bean
        UuidSupplier uuidSupplier() {
            return UUID::randomUUID;
        }
    }
}
