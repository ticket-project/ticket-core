package com.ticket.core.domain.performance.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BookingEntryResolverTest {

    @Test
    void returns_direct_entry_when_performance_does_not_require_queue() {
        BookingEntryResolver.Output output = BookingEntryResolver.resolve(
                10L,
                performance(QueueMode.FORCE_OFF),
                LocalDateTime.of(2026, 5, 24, 20, 0)
        );

        assertThat(output.entryType()).isEqualTo(BookingEntryResolver.EntryType.DIRECT);
        assertThat(output.queueRequired()).isFalse();
        assertThat(output.redirectUrl()).isEqualTo("/booking/seat?performanceId=10");
        assertThat(output.queueEnterUrl()).isNull();
    }

    @Test
    void returns_queue_entry_when_performance_requires_queue() {
        BookingEntryResolver.Output output = BookingEntryResolver.resolve(
                10L,
                performance(QueueMode.FORCE_ON),
                LocalDateTime.of(2026, 5, 24, 20, 0)
        );

        assertThat(output.entryType()).isEqualTo(BookingEntryResolver.EntryType.QUEUE);
        assertThat(output.queueRequired()).isTrue();
        assertThat(output.redirectUrl()).isNull();
        assertThat(output.queueEnterUrl()).isEqualTo("/api/v1/queue/performances/10/enter");
    }

    private Performance performance(final QueueMode queueMode) {
        Performance performance = new Performance(
                null,
                1L,
                LocalDateTime.of(2026, 5, 25, 20, 0),
                LocalDateTime.of(2026, 5, 25, 22, 0),
                LocalDateTime.of(2026, 5, 24, 20, 0),
                LocalDateTime.of(2026, 5, 24, 21, 0),
                4,
                300
        );
        performance.updateQueuePolicy(queueMode, QueueLevel.LEVEL_1, 300, 300, null, null, null);
        return performance;
    }
}
