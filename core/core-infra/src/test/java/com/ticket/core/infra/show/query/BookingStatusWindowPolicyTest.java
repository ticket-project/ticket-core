package com.ticket.core.infra.show.query;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ticket.core.domain.show.BookingStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class BookingStatusWindowPolicyTest {

    private final BookingStatusWindowPolicy bookingStatusWindowPolicy = new BookingStatusWindowPolicy();
    private final LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 15, 19, 0);

    @Test
    void bookingStatus가_null이면_null을_반환한다() {
        assertThat(bookingStatusWindowPolicy.condition(null, fixedNow)).isNull();
    }

    @Test
    void bookingStatus별로_고정시각_기준_조건식을_반환한다() {
        BooleanExpression beforeOpen = bookingStatusWindowPolicy.condition(BookingStatus.BEFORE_OPEN, fixedNow);
        BooleanExpression onSale = bookingStatusWindowPolicy.condition(BookingStatus.ON_SALE, fixedNow);
        BooleanExpression closed = bookingStatusWindowPolicy.condition(BookingStatus.CLOSED, fixedNow);

        assertThat(beforeOpen).isNotNull();
        assertThat(onSale).isNotNull();
        assertThat(closed).isNotNull();
        assertThat(beforeOpen.toString()).contains("2026-03-15T19:00");
        assertThat(onSale.toString()).contains("2026-03-15T19:00");
        assertThat(closed.toString()).contains("2026-03-15T19:00");
    }
}
