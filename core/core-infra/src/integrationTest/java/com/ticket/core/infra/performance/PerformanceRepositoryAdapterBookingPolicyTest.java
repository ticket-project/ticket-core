package com.ticket.core.infra.performance;

import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(PerformanceRepositoryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceRepositoryAdapterBookingPolicyTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private PerformanceRepository performanceRepository;

    @Test
    void 엔티티_대신_불변_예매정책을_조회한다() {
        LocalDateTime startTime = LocalDateTime.of(2026, 8, 10, 19, 0);
        LocalDateTime orderOpenTime = startTime.minusDays(10);
        LocalDateTime orderCloseTime = startTime.minusMinutes(10);
        LocalDateTime preopenQueueStartAt = orderOpenTime.minusMinutes(30);
        Performance performance = new Performance(
                null,
                1L,
                startTime,
                startTime.plusHours(2),
                orderOpenTime,
                orderCloseTime,
                4,
                300
        );
        performance.updateQueuePolicy(
                QueueMode.FORCE_ON,
                QueueLevel.LEVEL_2,
                preopenQueueStartAt,
                "잠시 기다려 주세요.",
                "부하 보호"
        );
        entityManager.persist(performance);
        flushAndClear();

        PerformanceBookingPolicyView policy = performanceRepository
                .findBookingPolicyById(performance.getId())
                .orElseThrow();

        assertThat(policy.performanceId()).isEqualTo(performance.getId());
        assertThat(policy.orderOpenTime()).isEqualTo(orderOpenTime);
        assertThat(policy.orderCloseTime()).isEqualTo(orderCloseTime);
        assertThat(policy.maxCanHoldCount()).isEqualTo(4);
        assertThat(policy.holdTime()).isEqualTo(300);
        assertThat(policy.queueMode()).isEqualTo(QueueMode.FORCE_ON);
        assertThat(policy.queueLevel()).isEqualTo(QueueLevel.LEVEL_2);
        assertThat(policy.preopenQueueStartAt()).isEqualTo(preopenQueueStartAt);
    }
}
