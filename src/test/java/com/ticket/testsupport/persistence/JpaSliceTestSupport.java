package com.ticket.testsupport.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import com.querydsl.jpa.impl.JPAQueryFactory;

/**
 * 실제 JPA·Querydsl을 H2에 붙여 검증하는 좁은 슬라이스 테스트의 베이스다. 업무 bean은 올리지 않는다 — 하위 클래스가 검증할 adapter·repository를 {@code @Import}한다.
 *
 * <p>스키마는 {@link MigratedSchema}가 운영 migration으로 만든다. entity와 Spring Data 인터페이스는 {@code com.ticket} 전체를 스캔한다. module을
 * 추출할 때마다 package 목록을 고치지 않기 위해서고, {@code @Repository} Spring Data 인터페이스가 아닌 클래스는 걸러지므로 넓게 잡아도 안전하다.
 *
 * <p>DB를 쓰는 업무 규칙 단위 테스트는 이 클래스를 쓰지 않는다. 별도 {@code integrationTest} source set은 없다(ADR 0003 §1).
 */
@MigratedSchema
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = JpaSliceTestSupport.SliceApplication.class)
@TestPropertySource(
        properties = {
            // Spring Modulith의 ModuleObservabilityAutoConfiguration은 기본으로 켜져(matchIfMissing=true)
            // ApplicationModulesRuntime을 즉시(non-lazy) 요구하는 BeanPostProcessor를 등록한다. 이 좁은
            // 슬라이스 컨텍스트는 실제 main class(@SpringBootApplication)가 없어 그 런타임을 만들 수
            // 없으므로, 이 슬라이스에서는 tracing 관측을 꺼서 그 자동설정 자체가 활성화되지 않게 한다.
            "management.tracing.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                    + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                    + "org.redisson.spring.starter.RedissonAutoConfigurationV4,"
                    // spring-modulith-actuator의 이 자동설정도 (tracing과 무관하게) ApplicationModulesRuntime을
                    // ObjectProvider.getObject()로 즉시 resolve한다. management.tracing.enabled와는 별개
                    // 원인이라 따로 꺼야 한다.
                    + "org.springframework.modulith.actuator.autoconfigure.ApplicationModulesEndpointConfiguration,"
                    // spring-modulith-runtime 자체의 이 자동설정은 항상 ApplicationModulesBootstrap을
                    // 만들며 classpath에서 @SpringBootApplication 애노테이션 클래스를 찾는다. 이 좁은
                    // 슬라이스는 그런 main class가 없으므로 이 자동설정 자체를 꺼서 부트스트랩 실패를 막는다.
                    + "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration"
        })
public abstract class JpaSliceTestSupport {
    // @TestComponent는 Spring Boot의 TypeExcludeFilter(TestTypeExcludeFilter)가 다른
    // @SpringBootTest 컨텍스트(TicketApplication 등)의 component scan에서 이 클래스를 제외하게
    // 한다. 이 테스트 전용 클래스가 실제 앱과 같은 com.ticket 패키지 트리 아래 있어,
    // @Modulith(=@SpringBootApplication)의 기본 component scan이 이 클래스까지 주워 담으면
    // clock() 같은 테스트 전용 빈이 실제 앱 빈과 충돌한다.
    // 주의: 여기 @TestConfiguration을 쓰면 안 된다 — SpringBootTestContextBootstrapper는
    // classes=... 로 명시한 설정이 전부 @TestConfiguration이면 "명시하지 않은 것"으로 보고
    // 패키지를 거슬러 올라가며 다른 @SpringBootConfiguration을 추가로 찾아 병합해버린다.
    // @TestComponent만 쓰면 TypeExcludeFilter 적용은 그대로 받으면서 그 자동 탐색-병합은 피한다.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @TestComponent
    @EntityScan("com.ticket")
    @EnableJpaRepositories("com.ticket")
    @EnableJpaAuditing
    static class SliceApplication {
        /** 조회 조건이 "지금"을 쓰는 repository가 있다. 테스트 fixture도 이 clock을 주입받아 시각을 만든다. */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        }

        @Bean
        JPAQueryFactory jpaQueryFactory(final EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("test-auditor");
        }
    }
}
