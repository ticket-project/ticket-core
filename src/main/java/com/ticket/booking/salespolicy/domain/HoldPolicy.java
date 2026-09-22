package com.ticket.booking.salespolicy.domain;

import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.ticket.shared.exception.InvalidRequestException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 회차 하나가 허용하는 Hold 정책이다. DB에는 {@code holdDurationSeconds}로 단위를 명시해 저장하고, 도메인에서는 {@link Duration}으로 다룬다 — {@code Integer
 * holdTime}처럼 단위를 숨기는 이름은 두지 않는다.
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
        validateHoldDuration(holdDuration);
        this.maxSeatCount = maxSeatCount;
        this.holdDurationSeconds = holdDuration.getSeconds();
    }

    /**
     * 저장 계약이 초 단위 정수라 {@link Duration}도 양의 정수 초여야 한다.
     *
     * <p>{@code isZero}/{@code isNegative}만 보면 1초 미만과 소수 초가 통과한다 — {@code Duration.ofMillis(500)}은 양수지만
     * {@code getSeconds()}가 0이라 hold TTL이 0으로 저장된다. 조용히 잘리는 대신 입력을 거부한다.
     */
    private static void validateHoldDuration(final Duration holdDuration) {
        if (holdDuration == null || holdDuration.isZero() || holdDuration.isNegative()) {
            throw new InvalidRequestException("holdDuration은 0보다 커야 합니다.");
        }
        if (holdDuration.getNano() != 0) {
            throw new InvalidRequestException(
                    "holdDuration은 초 단위 정수여야 합니다. 1초 미만이나 소수 초는 저장 시 잘립니다. holdDuration=" + holdDuration);
        }
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
