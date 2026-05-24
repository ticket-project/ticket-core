package com.ticket.core.domain.performance.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class GetBookingEntryUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-05-24T11:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final PerformanceFinder performanceFinder = org.mockito.Mockito.mock(PerformanceFinder.class);
    private final GetBookingEntryUseCase useCase = new GetBookingEntryUseCase(performanceFinder, CLOCK);

    @Test
    void returns_direct_entry_when_performance_does_not_require_queue() {
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_OFF));

        GetBookingEntryUseCase.Output output = useCase.execute(new GetBookingEntryUseCase.Input(10L));

        assertThat(output.entryType()).isEqualTo(GetBookingEntryUseCase.EntryType.DIRECT);
        assertThat(output.queueRequired()).isFalse();
        assertThat(output.redirectUrl()).isEqualTo("/booking/seat?performanceId=10");
        assertThat(output.queueEnterUrl()).isNull();
    }

    @Test
    void returns_queue_entry_when_performance_requires_queue() {
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));

        GetBookingEntryUseCase.Output output = useCase.execute(new GetBookingEntryUseCase.Input(10L));

        assertThat(output.entryType()).isEqualTo(GetBookingEntryUseCase.EntryType.QUEUE);
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