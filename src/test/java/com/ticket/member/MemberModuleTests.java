package com.ticket.member;

import com.ticket.shared.UuidSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.util.UUID;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 이미 전담한다. {@code spring.modulith.detection-strategy}를
 * 전역으로 바꾸는 대신 이 테스트에서만 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지
 * 않은 미래 모듈이 조용히 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.member} package tree만 component-scan한다.
 * {@code UuidSupplier}(공개 계약은 {@code com.ticket.shared.UuidSupplier}) bean은
 * {@code com.ticket.config.UuidSupplierConfig}에 있어
 * 그 필터 밖이므로, {@code RedisOAuth2AuthCodeStore} 같은 member 어댑터가 필요로 하는 bean만 이
 * 테스트 안에서 직접 채운다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
@Import(MemberModuleTests.TestSupportConfig.class)
class MemberModuleTests {

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
