package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.BaseEntity;
import com.ticket.core.domain.hold.model.Hold;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
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
        name = "ORDER_HOLD_CREATION_OUTBOX",
        indexes = {
                @Index(name = "IDX_ORDER_HOLD_CREATION_OUTBOX_DUE", columnList = "status,next_attempt_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldCreationOutbox extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long performanceId;

    @Column(nullable = false, length = 64)
    private String holdKey;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String seatIdsPayload;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(nullable = false)
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldCreationOutboxStatus status;

    private LocalDateTime completedAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    private HoldCreationOutbox(final Hold hold, final LocalDateTime nextAttemptAt) {
        this.memberId = hold.memberId();
        this.performanceId = hold.performanceId();
        this.holdKey = hold.holdKey();
        this.seatIdsPayload = serializeSeatIds(hold.seatIds());
        this.expiresAt = hold.expiresAt();
        this.nextAttemptAt = nextAttemptAt;
        this.retryCount = 0;
        this.status = HoldCreationOutboxStatus.PENDING;
    }

    public static HoldCreationOutbox create(final Hold hold, final LocalDateTime nextAttemptAt) {
        return new HoldCreationOutbox(hold, nextAttemptAt);
    }

    public Hold toHold() {
        return new Hold(holdKey, memberId, performanceId, seatIds(), expiresAt);
    }

    public boolean isCompleted() {
        return status == HoldCreationOutboxStatus.COMPLETED;
    }

    public void markCompleted(final LocalDateTime completedAt) {
        this.status = HoldCreationOutboxStatus.COMPLETED;
        this.completedAt = completedAt;
        this.lastError = null;
    }

    public void scheduleRetry(final LocalDateTime nextAttemptAt, final String errorMessage) {
        this.status = HoldCreationOutboxStatus.FAILED;
        this.nextAttemptAt = nextAttemptAt;
        this.retryCount++;
        this.lastError = summarize(errorMessage);
    }

    private List<Long> seatIds() {
        if (seatIdsPayload.isBlank()) {
            return List.of();
        }
        return Arrays.stream(seatIdsPayload.split(","))
                .map(Long::valueOf)
                .toList();
    }

    private static String serializeSeatIds(final List<Long> seatIds) {
        return seatIds.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String summarize(final String errorMessage) {
        final String source = errorMessage == null || errorMessage.isBlank()
                ? "hold creation post-commit failed"
                : errorMessage;
        if (source.length() <= MAX_ERROR_LENGTH) {
            return source;
        }
        return source.substring(0, MAX_ERROR_LENGTH);
    }
}
