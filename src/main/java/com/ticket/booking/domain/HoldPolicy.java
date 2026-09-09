package com.ticket.booking.domain;

import com.ticket.error.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * 회차 하나가 허용하는 Hold 정책이다. DB에는 {@code holdDurationSeconds}로 단위를 명시해 저장하고,
 * 도메인에서는 {@link Duration}으로 다룬다 — {@code Integer holdTime}처럼 단위를 숨기는 이름은
 * 두지 않는다.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldPolicy {

    @Column(name = "max_hold_seat_count")
    private Integer maxSeatCount;

    @Column(name = "hold_duration_seconds", nullable = false)
    private long holdDurationSeconds;

    public HoldPolicy(final Integer maxSeatCount, final Duration holdDuration) {
        if (maxSeatCount != null && maxSeatCount < 2) {
            throw new InvalidRequestException("maxSeatCount는 2 이상 또는 null 이어야 합니다.");
        }
        if (holdDuration == null || holdDuration.isZero() || holdDuration.isNegative()) {
            throw new InvalidRequestException("holdDuration은 0보다 커야 합니다.");
        }
        this.maxSeatCount = maxSeatCount;
        this.holdDurationSeconds = holdDuration.getSeconds();
    }

    public Integer maxSeatCount() {
        return maxSeatCount;
    }

    public Duration holdDuration() {
        return Duration.ofSeconds(holdDurationSeconds);
    }

    public boolean exceeds(final long requestedSeatCount) {
        return maxSeatCount != null && requestedSeatCount > maxSeatCount;
    }
}
