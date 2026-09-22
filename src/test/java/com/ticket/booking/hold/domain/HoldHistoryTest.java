package com.ticket.booking.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class HoldHistoryTest {
    @Test
    void 생성이력을_생성하면_created_이벤트로_기록된다() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        HoldHistory holdHistory = HoldHistory.created("hold-key", 1L, 10L, 100L, 200L, occurredAt, expiresAt);
        assertThat(holdHistory.getHoldKey()).isEqualTo("hold-key");
        assertThat(holdHistory.getMemberId()).isEqualTo(1L);
        assertThat(holdHistory.getPerformanceId()).isEqualTo(10L);
        assertThat(holdHistory.getPerformanceSeatId()).isEqualTo(100L);
        assertThat(holdHistory.getSeatId()).isEqualTo(200L);
        assertThat(holdHistory.getEventType()).isEqualTo(HoldHistoryEventType.CREATED);
        assertThat(holdHistory.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(holdHistory.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(holdHistory.getReleaseReason()).isNull();
    }

    @Test
    void 만료이력을_생성하면_expired_이벤트로_기록된다() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 30);
        HoldHistory holdHistory =
                HoldHistory.expired("hold-key", 1L, 10L, 100L, 200L, occurredAt, HoldReleaseReason.TTL_EXPIRED);
        assertThat(holdHistory.getEventType()).isEqualTo(HoldHistoryEventType.EXPIRED);
        assertThat(holdHistory.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(holdHistory.getExpiresAt()).isNull();
        assertThat(holdHistory.getReleaseReason()).isEqualTo(HoldReleaseReason.TTL_EXPIRED);
    }

    @Test
    void 취소이력을_생성하면_canceled_이벤트로_기록된다() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 10);
        HoldHistory holdHistory =
                HoldHistory.canceled("hold-key", 1L, 10L, 100L, 200L, occurredAt, HoldReleaseReason.USER_CANCELED);
        assertThat(holdHistory.getEventType()).isEqualTo(HoldHistoryEventType.CANCELED);
        assertThat(holdHistory.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(holdHistory.getExpiresAt()).isNull();
        assertThat(holdHistory.getReleaseReason()).isEqualTo(HoldReleaseReason.USER_CANCELED);
    }
}
