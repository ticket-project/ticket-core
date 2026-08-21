package com.ticket.core.domain.order.command.release;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Getter
@Entity
@Table(
        name = "ORDER_HOLD_RELEASE_OUTBOX",
        indexes = {
                @Index(name = "IDX_ORDER_HOLD_RELEASE_OUTBOX_DUE", columnList = "status,next_attempt_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldReleaseOutbox extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long performanceId;

    @Column(nullable = false, length = 64)
    private String holdKey;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String seatIdsPayload;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(nullable = false)
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldReleaseOutboxStatus status;

    private LocalDateTime completedAt;

    private LocalDateTime holdReleasedAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private HoldReleaseReason reason;

    private HoldReleaseOutbox(
            final Long performanceId,
            final String holdKey,
            final String seatIdsPayload,
            final LocalDateTime nextAttemptAt,
            final HoldReleaseReason reason
    ) {
        this.performanceId = performanceId;
        this.holdKey = holdKey;
        this.seatIdsPayload = seatIdsPayload;
        this.nextAttemptAt = nextAttemptAt;
        this.retryCount = 0;
        this.status = HoldReleaseOutboxStatus.PENDING;
        this.reason = reason;
    }

    public static HoldReleaseOutbox create(
            final Long performanceId,
            final String holdKey,
            final List<Long> seatIds,
            final LocalDateTime nextAttemptAt,
            final HoldReleaseReason reason
    ) {
        return new HoldReleaseOutbox(performanceId, holdKey, serializeSeatIds(seatIds), nextAttemptAt, reason);
    }

    public List<Long> seatIds() {
        if (seatIdsPayload.isBlank()) {
            return List.of();
        }
        return Arrays.stream(seatIdsPayload.split(","))
                .map(Long::valueOf)
                .toList();
    }

    public boolean isCompleted() {
        return status == HoldReleaseOutboxStatus.COMPLETED;
    }

    public boolean isHoldReleased() {
        return holdReleasedAt != null;
    }

    public void markHoldReleased(final LocalDateTime releasedAt) {
        if (holdReleasedAt == null) {
            this.holdReleasedAt = releasedAt;
        }
    }

    public void markCompleted(final LocalDateTime completedAt) {
        this.status = HoldReleaseOutboxStatus.COMPLETED;
        this.completedAt = completedAt;
        this.lastError = null;
    }

    public void scheduleRetry(final LocalDateTime nextAttemptAt, final String errorMessage) {
        this.status = HoldReleaseOutboxStatus.FAILED;
        this.nextAttemptAt = nextAttemptAt;
        this.retryCount++;
        this.lastError = summarize(errorMessage);
    }

    private static String serializeSeatIds(final List<Long> seatIds) {
        return seatIds.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String summarize(final String errorMessage) {
        final String source = errorMessage == null || errorMessage.isBlank()
                ? "hold release failed"
                : errorMessage;
        if (source.length() <= MAX_ERROR_LENGTH) {
            return source;
        }
        return source.substring(0, MAX_ERROR_LENGTH);
    }
}
