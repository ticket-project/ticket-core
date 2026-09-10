package com.ticket.shared.config;

import com.ticket.TicketApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 8 Step 8: Spring Modulith JPA event publication registry의 성공·실패·재제출 mechanics를
 * {@link Scenario} DSL로 검증한다.
 *
 * <p>{@link EventPublicationMaintenance}가 실제로 쓰는 {@link FailedEventPublications#resubmit}
 * 정책(batchSize=100, maxInFlight=4, completionAttempts&lt;=10)을 그대로 재현해 호출하므로, 이 정책이
 * 바뀌면 이 테스트도 갱신해야 한다. booking 도메인 이벤트가 아니라 이 테스트 전용 {@link ProbeEvent}로
 * registry 자체의 동작만 격리해서 본다 — booking listener의 업무 로직은
 * {@code com.ticket.booking} 아래의 다른 테스트가 고정한다.
 *
 * <p>고정된 재시도 정책과 deterministic fake({@link ProbeListener})만 쓰고 {@code Thread.sleep}은
 * 쓰지 않는다. 최초 비동기 전달 완료는 {@link Scenario#andWaitForStateChange}의 Awaitility 기반
 * polling으로 기다린다.
 */
@Slf4j
@SpringBootTest(
        classes = TicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:event-publication-maintenance-test;MODE=Oracle;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "JWT_SECRET=0123456789abcdef0123456789abcdef",
                "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=1800",
                "JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=1209600",
                "GOOGLE_CLIENT_ID=event-publication-maintenance-test",
                "GOOGLE_CLIENT_SECRET=event-publication-maintenance-test",
                "KAKAO_CLIENT_ID=event-publication-maintenance-test",
                "KAKAO_CLIENT_SECRET=event-publication-maintenance-test",
                "KAKAO_ADMIN_KEY=event-publication-maintenance-test",
                "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback",
                "OAUTH2_FAILURE_REDIRECT_URI=http://localhost:3000/auth/callback"
        }
)
@Import(EventPublicationMaintenanceScenarioTest.ProbeConfig.class)
@EnableScenarios
@SuppressWarnings({"NonAsciiCharacters", "resource"})
class EventPublicationMaintenanceScenarioTest {

    private static final int REDIS_PORT = 6379;

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private ProbeListener probeListener;

    @Autowired
    private CompletedEventPublications completedEventPublications;

    @Autowired
    private FailedEventPublications failedEventPublications;

    @BeforeEach
    void resetProbe() {
        probeListener.reset();
    }

    @Test
    void 첫_시도가_실패하면_FAILED로_기록되고_재제출이_성공하면_COMPLETED로_ARCHIVE된다(final Scenario scenario) {
        final UUID probeId = UUID.randomUUID();
        probeListener.failNextInvocations(1);

        scenario.publish(new ProbeEvent(probeId))
                .andWaitForStateChange(() -> probeListener.attempts(probeId), attempts -> attempts >= 1)
                .andVerify(attempts -> assertThat(attempts).isEqualTo(1));

        assertThat(probeListener.succeeded(probeId)).isFalse();
        assertThat(completedEventPublications.findAll())
                .noneMatch(publication -> matches(publication, probeId));

        resubmitAndAwait(scenario, probeId, 2);

        assertThat(probeListener.succeeded(probeId)).isTrue();
        assertThat(completedEventPublications.findAll())
                .anyMatch(publication -> matches(publication, probeId));
    }

    @Test
    void 십일회_초과해_실패한_publication은_자동_재제출_대상에서_제외된다(final Scenario scenario) {
        final UUID probeId = UUID.randomUUID();
        probeListener.alwaysFail(true);

        scenario.publish(new ProbeEvent(probeId))
                .andWaitForStateChange(() -> probeListener.attempts(probeId), attempts -> attempts >= 1)
                .andVerify(attempts -> assertThat(attempts).isEqualTo(1));

        // 최초 시도(1) + resubmit 10회 = completionAttempts 11. 정책은 <=10까지만 재시도 대상이므로
        // 이 10번은 전부 재제출 대상에 포함돼 다시 실패한다.
        for (int expectedAttempts = 2; expectedAttempts <= 11; expectedAttempts++) {
            resubmitAndAwait(scenario, probeId, expectedAttempts);
        }

        // completionAttempts가 11이 된 뒤에는 필터(<=10)가 더 이상 이 publication을 포함하지 않는다.
        // async listener가 뒤늦게라도 다시 불리지 않는지 짧게 확인한 뒤, count가 그대로인지 본다.
        resubmitFailedLikeMaintenance();
        org.awaitility.Awaitility.await()
                .pollDelay(Duration.ofMillis(300))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(probeListener.attempts(probeId)).isEqualTo(11));

        assertThat(completedEventPublications.findAll())
                .noneMatch(publication -> matches(publication, probeId));
    }

    /**
     * {@code resubmitFailedLikeMaintenance()}가 async listener를 다시 스케줄링만 하고 즉시
     * 반환하므로, 다음 재제출을 걸기 전에 이번 시도가 실제로 끝나기를 기다린다.
     */
    private void resubmitAndAwait(final Scenario scenario, final UUID probeId, final int expectedAttempts) {
        scenario.stimulate(this::resubmitFailedLikeMaintenance)
                .andWaitForStateChange(() -> probeListener.attempts(probeId), attempts -> attempts >= expectedAttempts)
                .andVerify(attempts -> assertThat(attempts).isEqualTo(expectedAttempts));
    }

    /**
     * {@link EventPublicationMaintenance#resubmitFailed()}와 정확히 같은 {@link ResubmissionOptions}로
     * 재제출한다. {@code EventPublicationMaintenance}는 package-private이라 이 테스트 패키지에서
     * 직접 호출할 수 있지만, 정책 값 자체를 이중으로 못박아 두는 쪽이 두 코드가 갈라졌을 때 더 잘 보인다.
     */
    private void resubmitFailedLikeMaintenance() {
        failedEventPublications.resubmit(
                ResubmissionOptions.defaults()
                        .withBatchSize(100)
                        .withMaxInFlight(4)
                        .withFilter(it -> it.getCompletionAttempts() <= 10));
    }

    private boolean matches(final EventPublication publication, final UUID probeId) {
        return publication.getEvent() instanceof ProbeEvent probeEvent && probeEvent.id().equals(probeId);
    }

    record ProbeEvent(UUID id) {
    }

    @Slf4j
    static class ProbeListener {

        private final ConcurrentHashMap<UUID, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<UUID, Boolean> succeeded = new ConcurrentHashMap<>();
        private final AtomicInteger remainingFailures = new AtomicInteger(0);
        private final AtomicBoolean alwaysFail = new AtomicBoolean(false);

        void reset() {
            attempts.clear();
            succeeded.clear();
            remainingFailures.set(0);
            alwaysFail.set(false);
        }

        void failNextInvocations(final int count) {
            remainingFailures.set(count);
        }

        void alwaysFail(final boolean value) {
            alwaysFail.set(value);
        }

        int attempts(final UUID id) {
            return attempts.getOrDefault(id, new AtomicInteger(0)).get();
        }

        boolean succeeded(final UUID id) {
            return succeeded.getOrDefault(id, false);
        }

        @ApplicationModuleListener
        void on(final ProbeEvent event) {
            attempts.computeIfAbsent(event.id(), __ -> new AtomicInteger(0)).incrementAndGet();

            if (alwaysFail.get() || remainingFailures.getAndUpdate(current -> current > 0 ? current - 1 : 0) > 0) {
                throw new IllegalStateException("probe listener가 의도적으로 실패합니다. eventId=" + event.id());
            }
            succeeded.put(event.id(), true);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfig {

        @Bean
        ProbeListener probeListener() {
            return new ProbeListener();
        }
    }
}
