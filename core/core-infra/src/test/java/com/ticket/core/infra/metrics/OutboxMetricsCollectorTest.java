package com.ticket.core.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticket.core.domain.order.command.create.HoldCreationOutbox;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxRepository;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxStatus;
import com.ticket.core.domain.order.command.release.HoldReleaseOutbox;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxRepository;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboxMetricsCollectorTest {

    private static final Instant NOW = Instant.parse("2026-08-12T03:00:00Z");

    @Test
    void exposes_current_outbox_counts_and_oldest_age() {
        HoldCreationOutboxRepository creationRepository = mock(HoldCreationOutboxRepository.class);
        HoldReleaseOutboxRepository releaseRepository = mock(HoldReleaseOutboxRepository.class);
        HoldCreationOutbox creation = mock(HoldCreationOutbox.class);
        HoldReleaseOutbox release = mock(HoldReleaseOutbox.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(creationRepository.countByStatus(HoldCreationOutboxStatus.PENDING)).thenReturn(3L);
        when(creationRepository.countByStatus(HoldCreationOutboxStatus.FAILED)).thenReturn(1L);
        when(creationRepository.findFirstByStatusInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(Optional.of(creation));
        when(creation.getCreatedAt()).thenReturn(LocalDateTime.ofInstant(NOW.minusSeconds(300), ZoneOffset.UTC));
        when(releaseRepository.countByStatus(HoldReleaseOutboxStatus.PENDING)).thenReturn(2L);
        when(releaseRepository.countByStatus(HoldReleaseOutboxStatus.FAILED)).thenReturn(4L);
        when(releaseRepository.findFirstByStatusInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(Optional.of(release));
        when(release.getCreatedAt()).thenReturn(LocalDateTime.ofInstant(NOW.minusSeconds(120), ZoneOffset.UTC));
        OutboxMetricsCollector collector = collector(creationRepository, releaseRepository, meterRegistry);

        collector.refresh();

        assertThat(gauge(meterRegistry, "booking.outbox.pending", "hold_creation")).isEqualTo(3.0);
        assertThat(gauge(meterRegistry, "booking.outbox.failed", "hold_creation")).isEqualTo(1.0);
        assertThat(gauge(meterRegistry, "booking.outbox.oldest.age", "hold_creation")).isEqualTo(300.0);
        assertThat(gauge(meterRegistry, "booking.outbox.pending", "hold_release")).isEqualTo(2.0);
        assertThat(gauge(meterRegistry, "booking.outbox.oldest.age", "hold_release")).isEqualTo(120.0);
    }

    @Test
    void records_query_failure_without_hiding_the_other_outbox_type() {
        HoldCreationOutboxRepository creationRepository = mock(HoldCreationOutboxRepository.class);
        HoldReleaseOutboxRepository releaseRepository = mock(HoldReleaseOutboxRepository.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(creationRepository.countByStatus(HoldCreationOutboxStatus.PENDING))
                .thenThrow(new IllegalStateException("db unavailable"));
        when(releaseRepository.countByStatus(HoldReleaseOutboxStatus.PENDING)).thenReturn(2L);
        when(releaseRepository.countByStatus(HoldReleaseOutboxStatus.FAILED)).thenReturn(0L);
        when(releaseRepository.findFirstByStatusInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(Optional.empty());
        OutboxMetricsCollector collector = collector(creationRepository, releaseRepository, meterRegistry);

        collector.refresh();

        assertThat(meterRegistry.get("booking.outbox.observation.failure")
                .tag("type", "hold_creation")
                .tag("reason", "outbox_metric_query_failure")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(gauge(meterRegistry, "booking.outbox.pending", "hold_release")).isEqualTo(2.0);
    }

    private OutboxMetricsCollector collector(
            final HoldCreationOutboxRepository creationRepository,
            final HoldReleaseOutboxRepository releaseRepository,
            final SimpleMeterRegistry meterRegistry
    ) {
        return new OutboxMetricsCollector(
                creationRepository,
                releaseRepository,
                new CoreBookingMetrics(meterRegistry),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private double gauge(
            final SimpleMeterRegistry meterRegistry,
            final String name,
            final String type
    ) {
        return meterRegistry.get(name).tag("type", type).gauge().value();
    }
}
