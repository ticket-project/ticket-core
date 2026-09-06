package com.ticket.booking.domain.hold.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class HoldTest {

    @Test
    void hold는_생성값을_그대로_보관한다() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 15, 12, 30);

        Hold hold = new Hold("hold-key", 1L, 10L, List.of(100L, 101L), expiresAt);

        assertThat(hold.holdKey()).isEqualTo("hold-key");
        assertThat(hold.memberId()).isEqualTo(1L);
        assertThat(hold.performanceId()).isEqualTo(10L);
        assertThat(hold.seatIds()).containsExactly(100L, 101L);
        assertThat(hold.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void 유효시간은_도메인이_결정해_만료시각으로_표현한다() {
        LocalDateTime now = LocalDateTime.of(2026, 3, 15, 12, 25);

        Hold hold = Hold.create("hold-key", 1L, 10L, List.of(100L), now, Duration.ofMinutes(5));

        assertThat(hold.expiresAt()).isEqualTo(LocalDateTime.of(2026, 3, 15, 12, 30));
    }

    @Test
    void 시작시각은_만료시각에서_유효시간을_뺀_값이다() {
        Hold hold = new Hold("hold-key", 1L, 10L, List.of(100L), LocalDateTime.of(2026, 3, 15, 12, 30));

        assertThat(hold.startedAt(Duration.ofMinutes(5)))
                .isEqualTo(LocalDateTime.of(2026, 3, 15, 12, 25));
    }
}
