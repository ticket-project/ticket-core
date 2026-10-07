package com.ticket.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;

import com.ticket.testsupport.CoreApplicationTestSupport;

/**
 * Task 8 Step 8: Spring Modulith JPA event publication registry의 성공·실패·재처리 mechanics를 {@link Scenario} DSL로 검증한다.
 *
 * <p>재처리는 운영 코드 {@link EventPublicationMaintenance#resubmitFailed()}를 그대로 부른다. 그래서 재시도 정책(batchSize, maxInFlight,
 * completionAttempts&lt;=10)이 바뀌면 이 테스트가 그 정책으로 검증한다. 운영 스케줄러가 같은 메서드를 1분마다 불러 테스트와 겹치지 않도록
 * {@code worker.enabled=false}로 스케줄링을 끈다. booking 도메인 이벤트가 아니라 이 테스트 전용 {@link ProbeEvent}로 registry 자체의 동작만 격리해서 본다 —
 * booking listener의 업무 로직은 {@code com.ticket.booking} 아래의 다른 테스트가 고정한다.
 *
 * <p>deterministic fake({@link ProbeListener})만 쓰고 {@code Thread.sleep}은 쓰지 않는다. 최초 비동기 전달 완료는
 * {@link Scenario#andWaitForStateChange}의 Awaitility 기반 polling으로 기다린다.
 */
@TestPropertySource(properties = "worker.enabled=false")
@Import(EventPublicationMaintenanceScenarioTest.ProbeConfig.class)
@EnableScenarios
@SuppressWarnings("NonAsciiCharacters")
class EventPublicationMaintenanceScenarioTest extends CoreApplicationTestSupport {
    @Autowired
    private EventPublicationMaintenance eventPublicationMaintenance;

    @Autowired
    private ProbeListener probeListener;

    @Autowired
    private CompletedEventPublications completedEventPublications;

    @BeforeEach
    void resetProbe() {
        probeListener.reset();
    }

    @Test
    void 첫_시도가_실패하면_FAILED로_기록되고_재처리가_성공하면_COMPLETED로_ARCHIVE된다(final Scenario scenario) {
        final UUID probeId = UUID.randomUUID();
        probeListener.failNextInvocations(1);

        scenario.publish(new ProbeEvent(probeId))
                .andWaitForStateChange(() -> probeListener.attempts(probeId), attempts -> attempts >= 1)
                .andVerify(attempts -> assertThat(attempts).isEqualTo(1));

        assertThat(probeListener.succeeded(probeId)).isFalse();
        assertThat(completedEventPublications.findAll()).noneMatch(publication -> matches(publication, probeId));

        resubmitAndAwait(scenario, probeId, 2);

        assertThat(probeListener.succeeded(probeId)).isTrue();
        assertThat(completedEventPublications.findAll()).anyMatch(publication -> matches(publication, probeId));
    }

    @Test
    void 십일회_초과해_실패한_publication은_자동_재처리_대상에서_제외된다(final Scenario scenario) {
        final UUID probeId = UUID.randomUUID();
        probeListener.alwaysFail(true);

        scenario.publish(new ProbeEvent(probeId))
                .andWaitForStateChange(() -> probeListener.attempts(probeId), attempts -> attempts >= 1)
                .andVerify(attempts -> assertThat(attempts).isEqualTo(1));
        // 최초 시도(1) + resubmit 10회 = completionAttempts 11. 정책은 <=10까지만 재시도 대상이므로
        // 이 10번은 전부 재처리 대상에 포함돼 다시 실패한다.
        for (int expectedAttempts = 2; expectedAttempts <= 11; expectedAttempts++) {
            resubmitAndAwait(scenario, probeId, expectedAttempts);
        }
        // completionAttempts가 11이 된 뒤에는 필터(<=10)가 더 이상 이 publication을 포함하지 않는다.
        // async listener가 뒤늦게라도 다시 불리지 않는지 짧게 확인한 뒤, count가 그대로인지 본다.
        eventPublicationMaintenance.resubmitFailed();
        org.awaitility.Awaitility.await()
                .pollDelay(Duration.ofMillis(300))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(probeListener.attempts(probeId)).isEqualTo(11));

        assertThat(completedEventPublications.findAll()).noneMatch(publication -> matches(publication, probeId));
    }

    /**
     * {@code eventPublicationMaintenance.resubmitFailed()}가 async listener를 다시 스케줄링만 하고 즉시 반환하므로, 다음 재처리를 걸기 전에 이번 시도가
     * 실제로 끝나기를 기다린다.
     */
    private void resubmitAndAwait(final Scenario scenario, final UUID probeId, final int expectedAttempts) {
        scenario.stimulate(eventPublicationMaintenance::resubmitFailed)
                .andWaitForStateChange(() -> probeListener.attempts(probeId), attempts -> attempts >= expectedAttempts)
                .andVerify(attempts -> assertThat(attempts).isEqualTo(expectedAttempts));
    }

    private boolean matches(final EventPublication publication, final UUID probeId) {
        return publication.getEvent() instanceof ProbeEvent probeEvent
                && probeEvent.id().equals(probeId);
    }

    record ProbeEvent(UUID id) {}

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
