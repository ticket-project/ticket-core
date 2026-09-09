package com.ticket.booking.domain;

import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class OrderAcceptanceWindowTest {

    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2026, 6, 1, 10, 0);

    @Test
    void 시작_전이면_BEFORE_OPEN이다() {
        OrderAcceptanceWindow window = new OrderAcceptanceWindow(OPENS_AT, CLOSES_AT);

        assertThat(window.statusAt(OPENS_AT.minusMinutes(1))).isEqualTo(OrderAcceptanceStatus.BEFORE_OPEN);
    }

    @Test
    void 시작_시각과_같으면_OPEN이다() {
        OrderAcceptanceWindow window = new OrderAcceptanceWindow(OPENS_AT, CLOSES_AT);

        assertThat(window.statusAt(OPENS_AT)).isEqualTo(OrderAcceptanceStatus.OPEN);
    }

    @Test
    void 기간_중이면_OPEN이다() {
        OrderAcceptanceWindow window = new OrderAcceptanceWindow(OPENS_AT, CLOSES_AT);

        assertThat(window.statusAt(OPENS_AT.plusDays(1))).isEqualTo(OrderAcceptanceStatus.OPEN);
    }

    @Test
    void 마감_시각과_같으면_OPEN이다() {
        OrderAcceptanceWindow window = new OrderAcceptanceWindow(OPENS_AT, CLOSES_AT);

        assertThat(window.statusAt(CLOSES_AT)).isEqualTo(OrderAcceptanceStatus.OPEN);
    }

    @Test
    void 마감_이후면_CLOSED다() {
        OrderAcceptanceWindow window = new OrderAcceptanceWindow(OPENS_AT, CLOSES_AT);

        assertThat(window.statusAt(CLOSES_AT.plusMinutes(1))).isEqualTo(OrderAcceptanceStatus.CLOSED);
    }

    @Test
    void opensAt이_null이면_예외를_던진다() {
        assertThatThrownBy(() -> new OrderAcceptanceWindow(null, CLOSES_AT))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void closesAt이_null이면_예외를_던진다() {
        assertThatThrownBy(() -> new OrderAcceptanceWindow(OPENS_AT, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void opensAt이_closesAt보다_늦거나_같으면_예외를_던진다() {
        assertThatThrownBy(() -> new OrderAcceptanceWindow(CLOSES_AT, OPENS_AT))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new OrderAcceptanceWindow(OPENS_AT, OPENS_AT))
                .isInstanceOf(InvalidRequestException.class);
    }
}
