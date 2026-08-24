package com.ticket.core.domain.performance.query.model;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PerformanceBookingPolicyViewTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Test
    void 예매가능시간_안이면_통과한다() {
        PerformanceBookingPolicyView policy = policy(NOW.minusMinutes(1), NOW.plusMinutes(1));

        assertThatCode(() -> policy.ensureBookingOpenAt(NOW)).doesNotThrowAnyException();
    }

    @Test
    void 예매시작_전이면_실패한다() {
        PerformanceBookingPolicyView policy = policy(NOW.plusMinutes(1), NOW.plusHours(1));

        assertError(policy, ErrorType.NOT_YET_RESERVE_TIME);
    }

    @Test
    void 예매마감_후면_실패한다() {
        PerformanceBookingPolicyView policy = policy(NOW.minusHours(1), NOW.minusMinutes(1));

        assertError(policy, ErrorType.PERFORMANCE_IS_PAST);
    }

    @Test
    void 예매시작시각이_없으면_실패한다() {
        PerformanceBookingPolicyView policy = policy(null, NOW.plusHours(1));

        assertError(policy, ErrorType.NOT_YET_RESERVE_TIME);
    }

    @Test
    void 예매마감시각이_없으면_실패한다() {
        PerformanceBookingPolicyView policy = policy(NOW.minusHours(1), null);

        assertError(policy, ErrorType.PERFORMANCE_IS_PAST);
    }

    @Test
    void 판정은_전달받은_시각을_기준으로_한다() {
        PerformanceBookingPolicyView policy = policy(NOW.minusMinutes(1), NOW.plusMinutes(1));

        assertThatCode(() -> policy.ensureBookingOpenAt(NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.ensureBookingOpenAt(NOW.plusHours(1)))
                .isInstanceOf(CoreException.class);
    }

    private void assertError(final PerformanceBookingPolicyView policy, final ErrorType errorType) {
        assertThatThrownBy(() -> policy.ensureBookingOpenAt(NOW))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(errorType));
    }

    private PerformanceBookingPolicyView policy(
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime
    ) {
        return new PerformanceBookingPolicyView(
                10L,
                orderOpenTime,
                orderCloseTime,
                4,
                300,
                null,
                null,
                null,
                null,
                null
        );
    }
}
