package com.ticket.booking.event.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticket.booking.event.HoldReleaseProgressRecorder;

/**
 * 선점 해제 완료 기록의 커밋 경계와 멱등성을 실제 DB에서 고정한다.
 *
 * <p>재현한 결함: Redis 해제를 마치고 완료 기록을 남긴 뒤 WebSocket 발행이 실패하면, 완료 기록이 호출자의 트랜잭션과 함께 롤백돼 재시도에서 Redis 해제를
 * 다시 수행했다. 기록은 자기 트랜잭션에서 곧바로 커밋돼야 한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = HoldReleaseProgressRecorderAdapterIntegrationTest.TestApplication.class)
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:hold-release-progress-test;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.show-sql=false",
            "management.tracing.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                    + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                    + "org.redisson.spring.starter.RedissonAutoConfigurationV4,"
                    + "org.springframework.modulith.actuator.autoconfigure.ApplicationModulesEndpointConfiguration,"
                    + "org.springframework.modulith.runtime.autoconfigure.SpringModulithRuntimeAutoConfiguration"
        })
@SuppressWarnings("NonAsciiCharacters")
class HoldReleaseProgressRecorderAdapterIntegrationTest {
    private static final LocalDateTime RELEASED_AT = LocalDateTime.of(2026, 3, 15, 12, 0);

    @Autowired private HoldReleaseProgressRecorder recorder;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void 완료_기록은_호출자_트랜잭션이_롤백돼도_남는다() {
        final UUID eventId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                new TransactionTemplate(transactionManager)
                                        .executeWithoutResult(
                                                status -> {
                                                    recorder.recordHoldReleased(
                                                            eventId, RELEASED_AT);
                                                    // Redis 해제 뒤의 WebSocket 발행 실패를 흉내 낸다.
                                                    throw new IllegalStateException("발행 실패");
                                                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(recorder.isReleased(eventId)).isTrue();
    }

    @Test
    void 같은_eventId를_다시_기록해도_예외_없이_한_건만_남는다() {
        final UUID eventId = UUID.randomUUID();

        recorder.recordHoldReleased(eventId, RELEASED_AT);
        recorder.recordHoldReleased(eventId, RELEASED_AT.plusMinutes(1));

        assertThat(recorder.isReleased(eventId)).isTrue();
    }

    @Test
    void 기록하지_않은_eventId는_해제되지_않은_것으로_본다() {
        assertThat(recorder.isReleased(UUID.randomUUID())).isFalse();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @TestComponent
    @EntityScan(basePackages = "com.ticket.booking.event.persistence")
    @EnableJpaRepositories(basePackageClasses = SpringDataHoldReleaseProgressJpaRepository.class)
    @EnableJpaAuditing
    @Import(HoldReleaseProgressRecorderAdapter.class)
    static class TestApplication {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("integration-test");
        }
    }
}
