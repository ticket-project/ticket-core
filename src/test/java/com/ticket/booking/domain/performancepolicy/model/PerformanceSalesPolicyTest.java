package com.ticket.booking.domain.performancepolicy.model;

import com.ticket.booking.exception.ExceedHoldLimitException;
import com.ticket.booking.exception.NotYetReserveTimeException;
import com.ticket.booking.exception.PerformanceIsPastException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PerformanceSalesPolicyTest {

    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2026, 6, 1, 10, 0);

    @Test
    void 시작_전이면_NotYetReserveTimeException을_던진다() {
        PerformanceSalesPolicy policy = policy(4, 600, null);

        assertThatThrownBy(() -> policy.ensureAcceptingOrders(OPENS_AT.minusMinutes(1)))
                .isInstanceOf(NotYetReserveTimeException.class);
    }

    @Test
    void 마감_이후면_PerformanceIsPastException을_던진다() {
        PerformanceSalesPolicy policy = policy(4, 600, null);

        assertThatThrownBy(() -> policy.ensureAcceptingOrders(CLOSES_AT.plusMinutes(1)))
                .isInstanceOf(PerformanceIsPastException.class);
    }

    @Test
    void 접수_기간_안이면_예외를_던지지_않는다() {
        PerformanceSalesPolicy policy = policy(4, 600, null);

        assertThatCode(() -> policy.ensureAcceptingOrders(OPENS_AT.plusDays(1))).doesNotThrowAnyException();
    }

    @Test
    void 한도를_초과하면_ExceedHoldLimitException을_던진다() {
        PerformanceSalesPolicy policy = policy(2, 600, null);

        assertThatThrownBy(() -> policy.ensureWithinHoldLimit(3))
                .isInstanceOf(ExceedHoldLimitException.class);
    }

    @Test
    void 한도가_없으면_요청_수량을_제한하지_않는다() {
        PerformanceSalesPolicy policy = policy(null, 600, null);

        assertThatCode(() -> policy.ensureWithinHoldLimit(1_000)).doesNotThrowAnyException();
    }

    @Test
    void 대기열_필요_여부는_bookingEntryPolicy에_위임한다() {
        PerformanceSalesPolicy forceOn = policy(4, 600, QueueMode.FORCE_ON);
        PerformanceSalesPolicy none = policy(4, 600, null);

        assertThat(forceOn.isQueueRequired(OPENS_AT.plusDays(1))).isTrue();
        assertThat(none.isQueueRequired(OPENS_AT.plusDays(1))).isFalse();
    }

    @Test
    void holdDuration은_holdPolicy에서_얻는다() {
        PerformanceSalesPolicy policy = policy(4, 900, null);

        assertThat(policy.holdDuration()).isEqualTo(Duration.ofSeconds(900));
        assertThat(policy.maxSeatCount()).isEqualTo(4);
    }

    private PerformanceSalesPolicy policy(final Integer maxSeatCount, final int holdSeconds, final QueueMode queueMode) {
        return new PerformanceSalesPolicy(
                1L,
                new OrderAcceptanceWindow(OPENS_AT, CLOSES_AT),
                new HoldPolicy(maxSeatCount, Duration.ofSeconds(holdSeconds)),
                queueMode == null ? BookingEntryPolicy.none() : new BookingEntryPolicy(queueMode, null, null, null, null)
        );
    }
}
