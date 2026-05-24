package com.ticket.core.domain.performance.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PerformanceQueuePolicyTest {

    @Test
    void force_on_requires_queue() {
        Performance performance = performance();
        performance.updateQueuePolicy(QueueMode.FORCE_ON, QueueLevel.LEVEL_1, 300, 300, null, null, null);

        assertThat(performance.requiresQueueAt(LocalDateTime.of(2026, 5, 24, 19, 0))).isTrue();
    }

    @Test
    void force_off_and_empty_policy_do_not_require_queue() {
        Performance emptyPolicy = performance();
        assertThat(emptyPolicy.requiresQueueAt(LocalDateTime.of(2026, 5, 24, 19, 0))).isFalse();

        Performance forceOff = performance();
        forceOff.updateQueuePolicy(QueueMode.FORCE_OFF, QueueLevel.LEVEL_1, 300, 300, null, null, null);
        assertThat(forceOff.requiresQueueAt(LocalDateTime.of(2026, 5, 24, 19, 0))).isFalse();
    }

    @Test
    void auto_requires_queue_after_preopen_start_until_booking_close() {
        LocalDateTime open = LocalDateTime.of(2026, 5, 24, 20, 0);
        LocalDateTime close = LocalDateTime.of(2026, 5, 24, 21, 0);
        Performance performance = performance(open, close);
        performance.updateQueuePolicy(
                QueueMode.AUTO,
                QueueLevel.LEVEL_1,
                300,
                300,
                LocalDateTime.of(2026, 5, 24, 19, 50),
                null,
                null
        );

        assertThat(performance.requiresQueueAt(LocalDateTime.of(2026, 5, 24, 19, 49))).isFalse();
        assertThat(performance.requiresQueueAt(LocalDateTime.of(2026, 5, 24, 19, 50))).isTrue();
        assertThat(performance.requiresQueueAt(LocalDateTime.of(2026, 5, 24, 20, 30))).isTrue();
        assertThat(performance.requiresQueueAt(LocalDateTime.of(2026, 5, 24, 21, 1))).isFalse();
    }

    private Performance performance() {
        return performance(
                LocalDateTime.of(2026, 5, 24, 20, 0),
                LocalDateTime.of(2026, 5, 24, 21, 0)
        );
    }

    private Performance performance(final LocalDateTime orderOpenTime, final LocalDateTime orderCloseTime) {
        return new Performance(
                null,
                1L,
                LocalDateTime.of(2026, 5, 25, 20, 0),
                LocalDateTime.of(2026, 5, 25, 22, 0),
                orderOpenTime,
                orderCloseTime,
                4,
                300
        );
    }
}