package com.ticket.core.infra.metrics;

import com.ticket.core.domain.order.command.create.HoldCreationOutbox;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxRepository;
import com.ticket.core.domain.order.command.create.HoldCreationOutboxStatus;
import com.ticket.core.domain.order.command.release.HoldReleaseOutbox;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxRepository;
import com.ticket.core.domain.order.command.release.HoldReleaseOutboxStatus;
import com.ticket.core.infra.metrics.CoreBookingMetrics.OutboxType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMetricsCollector {

    private static final List<HoldCreationOutboxStatus> CREATION_OUTSTANDING = List.of(
            HoldCreationOutboxStatus.PENDING,
            HoldCreationOutboxStatus.FAILED
    );
    private static final List<HoldReleaseOutboxStatus> RELEASE_OUTSTANDING = List.of(
            HoldReleaseOutboxStatus.PENDING,
            HoldReleaseOutboxStatus.FAILED
    );

    private final HoldCreationOutboxRepository holdCreationOutboxRepository;
    private final HoldReleaseOutboxRepository holdReleaseOutboxRepository;
    private final CoreBookingMetrics metrics;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${management.metrics.booking.outbox.refresh-interval-ms:60000}")
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(clock);
        refreshCreation(now);
        refreshRelease(now);
    }

    private void refreshCreation(final LocalDateTime now) {
        try {
            long pending = holdCreationOutboxRepository.countByStatus(HoldCreationOutboxStatus.PENDING);
            long failed = holdCreationOutboxRepository.countByStatus(HoldCreationOutboxStatus.FAILED);
            long oldestAgeMillis = holdCreationOutboxRepository
                    .findFirstByStatusInOrderByCreatedAtAsc(CREATION_OUTSTANDING)
                    .map(HoldCreationOutbox::getCreatedAt)
                    .map(createdAt -> ageMillis(createdAt, now))
                    .orElse(0L);
            metrics.updateOutbox(OutboxType.HOLD_CREATION, pending, failed, oldestAgeMillis);
        } catch (RuntimeException exception) {
            metrics.recordOutboxObservationFailure(OutboxType.HOLD_CREATION);
            log.warn(
                    "outbox metrics collection failed reason=outbox_metric_query_failure outboxType=hold_creation",
                    exception
            );
        }
    }

    private void refreshRelease(final LocalDateTime now) {
        try {
            long pending = holdReleaseOutboxRepository.countByStatus(HoldReleaseOutboxStatus.PENDING);
            long failed = holdReleaseOutboxRepository.countByStatus(HoldReleaseOutboxStatus.FAILED);
            long oldestAgeMillis = holdReleaseOutboxRepository
                    .findFirstByStatusInOrderByCreatedAtAsc(RELEASE_OUTSTANDING)
                    .map(HoldReleaseOutbox::getCreatedAt)
                    .map(createdAt -> ageMillis(createdAt, now))
                    .orElse(0L);
            metrics.updateOutbox(OutboxType.HOLD_RELEASE, pending, failed, oldestAgeMillis);
        } catch (RuntimeException exception) {
            metrics.recordOutboxObservationFailure(OutboxType.HOLD_RELEASE);
            log.warn(
                    "outbox metrics collection failed reason=outbox_metric_query_failure outboxType=hold_release",
                    exception
            );
        }
    }

    private long ageMillis(final LocalDateTime createdAt, final LocalDateTime now) {
        return Math.max(0L, Duration.between(createdAt, now).toMillis());
    }
}
