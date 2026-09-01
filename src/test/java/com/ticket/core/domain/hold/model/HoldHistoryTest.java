package com.ticket.core.domain.hold.model;

import com.ticket.core.domain.hold.model.HoldHistoryEventType;
import com.ticket.core.domain.hold.model.HoldReleaseReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class HoldHistoryTest {

    @Test
    void 생성이력을_생성하면_created_이벤트로_기록된다() {
        //given
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);

        //when
        HoldHistory holdHistory = HoldHistory.created(
                "hold-key",
                1L,
                10L,
                100L,
                200L,
                occurredAt,
                expiresAt
        );

        //then
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
        //given
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 30);

        //when
        HoldHistory holdHistory = HoldHistory.expired(
                "hold-key",
                1L,
                10L,
                100L,
                200L,
                occurredAt,
                HoldReleaseReason.TTL_EXPIRED
        );

        //then
        assertThat(holdHistory.getEventType()).isEqualTo(HoldHistoryEventType.EXPIRED);
        assertThat(holdHistory.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(holdHistory.getExpiresAt()).isNull();
        assertThat(holdHistory.getReleaseReason()).isEqualTo(HoldReleaseReason.TTL_EXPIRED);
    }

    @Test
    void 취소이력을_생성하면_canceled_이벤트로_기록된다() {
        //given
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 15, 12, 10);

        //when
        HoldHistory holdHistory = HoldHistory.canceled(
                "hold-key",
                1L,
                10L,
                100L,
                200L,
                occurredAt,
                HoldReleaseReason.USER_CANCELED
        );

        //then
        assertThat(holdHistory.getEventType()).isEqualTo(HoldHistoryEventType.CANCELED);
        assertThat(holdHistory.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(holdHistory.getExpiresAt()).isNull();
        assertThat(holdHistory.getReleaseReason()).isEqualTo(HoldReleaseReason.USER_CANCELED);
    }
}
