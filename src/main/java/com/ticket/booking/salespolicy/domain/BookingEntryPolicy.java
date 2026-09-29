package com.ticket.booking.salespolicy.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import org.jspecify.annotations.Nullable;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 회차의 대기열 진입 정책이다. 옛 {@code show.domain.performance.QueueActivation}의 판정 규칙을 의미 손실 없이 그대로 옮긴다 — {@code queueMode}가
 * 없으면(옛 queue policy row가 없던 회차) 대기열을 요구하지 않는다.
 *
 * <p>테이블에는 {@code queue_level}·{@code waiting_room_message}·{@code queue_policy_reason} 열도 있지만 Core가 읽는 곳이 없어 매핑하지 않는다
 * — seed가 채우는 값이고, 대기열 판정과 예매 방식 응답에 들어가지 않는다.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingEntryPolicy {
    // 두 값 모두 선택 항목이다 — none()이 전부 null인 정책을 만들고, 옛 queue policy row가 없던 회차도 그렇다.
    @Enumerated(EnumType.STRING)
    @Column(name = "queue_mode", length = 20)
    private @Nullable QueueMode queueMode;

    @Column(name = "preopen_queue_starts_at")
    private @Nullable LocalDateTime preopenQueueStartAt;

    public BookingEntryPolicy(final @Nullable QueueMode queueMode, final @Nullable LocalDateTime preopenQueueStartAt) {
        this.queueMode = queueMode;
        this.preopenQueueStartAt = preopenQueueStartAt;
    }

    public static BookingEntryPolicy none() {
        return new BookingEntryPolicy(null, null);
    }

    public @Nullable QueueMode queueMode() {
        return queueMode;
    }

    public @Nullable LocalDateTime preopenQueueStartAt() {
        return preopenQueueStartAt;
    }

    /** 대기열을 태워야 하는 시각인지 판정한다. {@code orderClosesAt}은 이 회차의 {@link OrderAcceptanceWindow#closesAt}이다. */
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
