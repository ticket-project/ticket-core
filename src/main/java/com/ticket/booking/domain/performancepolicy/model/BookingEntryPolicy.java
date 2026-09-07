package com.ticket.booking.domain.performancepolicy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회차의 대기열 진입 정책이다. 옛 {@code show.domain.performance.QueueActivation}의 판정 규칙을
 * 의미 손실 없이 그대로 옮긴다 — {@code queueMode}가 없으면(옛 queue policy row가 없던 회차) 대기열을
 * 요구하지 않는다.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingEntryPolicy {

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_mode", length = 20)
    private QueueMode queueMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_level", length = 20)
    private QueueLevel queueLevel;

    @Column(name = "preopen_queue_starts_at")
    private LocalDateTime preopenQueueStartAt;

    @Column(name = "waiting_room_message", length = 255)
    private String waitingRoomMessage;

    @Column(name = "queue_policy_reason", length = 255)
    private String reason;

    public BookingEntryPolicy(
            final QueueMode queueMode,
            final QueueLevel queueLevel,
            final LocalDateTime preopenQueueStartAt,
            final String waitingRoomMessage,
            final String reason
    ) {
        this.queueMode = queueMode;
        this.queueLevel = queueLevel;
        this.preopenQueueStartAt = preopenQueueStartAt;
        this.waitingRoomMessage = waitingRoomMessage;
        this.reason = reason;
    }

    public static BookingEntryPolicy none() {
        return new BookingEntryPolicy(null, null, null, null, null);
    }

    public QueueMode queueMode() {
        return queueMode;
    }

    public QueueLevel queueLevel() {
        return queueLevel;
    }

    public LocalDateTime preopenQueueStartAt() {
        return preopenQueueStartAt;
    }

    public String waitingRoomMessage() {
        return waitingRoomMessage;
    }

    public String reason() {
        return reason;
    }

    /**
     * 대기열을 태워야 하는 시각인지 판정한다. {@code orderClosesAt}은 이 회차의
     * {@link OrderAcceptanceWindow#getClosesAt()}이다.
     */
    public boolean isRequiredAt(final LocalDateTime now, final LocalDateTime orderClosesAt) {
        if (queueMode == null || queueMode == QueueMode.FORCE_OFF) {
            return false;
        }
        if (queueMode == QueueMode.FORCE_ON) {
            return true;
        }
        if (preopenQueueStartAt == null || now == null || now.isBefore(preopenQueueStartAt)) {
            return false;
        }
        return orderClosesAt == null || !now.isAfter(orderClosesAt);
    }
}
