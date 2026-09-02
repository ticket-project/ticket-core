package com.ticket.catalog;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.time.Clock;

/**
 * {@code verifyAutomatically = false}: 전체 애플리케이션 구조 검증({@code ApplicationModules.verify()})은
 * {@code com.ticket.ModularityTests}가 legacy package를 제외한 predicate로 이미 전담한다. 기본값(true)으로
 * 두면 이 STANDALONE 테스트가 별도로 {@code verify()}를 실행하는데, catalog가 의존하는
 * {@code com.ticket.core.support.exception}이 legacy {@code com.ticket.core} 아래에 있고, PerformanceSeat
 * 등 아직 이동하지 않은 legacy 코드가 이번에 옮긴 catalog entity(Performance, Seat, Show)를 그대로
 * 참조해 "catalog → core"·"core → catalog" 순환으로 오탐된다. {@code spring.modulith.detection-strategy}를
 * 전역으로 바꾸는 대신 이 테스트에서만 자동 검증을 꺼서, 아직 {@code @ApplicationModule}을 붙이지 않은
 * 미래 모듈이 조용히 검증에서 빠지는 위험을 피한다.
 *
 * <p>STANDALONE bootstrap mode는 {@code com.ticket.catalog} package tree만 component-scan하고, 그 밖의
 * package에 있는 {@code @Configuration}은 {@code @Import}로 끌어와도 같은 필터에 걸려 등록되지 않는다.
 * {@code JPAQueryFactory} bean은 아직 legacy {@code com.ticket.core.infra.config.QuerydslConfig}에 있어
 * 그 필터 밖이므로, 이 테스트 안에서 직접 정의해 catalog 어댑터가 필요로 하는 bean만 채운다.
 */
@ApplicationModuleTest(verifyAutomatically = false)
@Import(CatalogModuleTests.TestQuerydslConfig.class)
class CatalogModuleTests {

    @Test
    void bootstraps() {
    }

    static class TestQuerydslConfig {

        @Bean
        JPAQueryFactory jpaQueryFactory(final EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }
    }
}
