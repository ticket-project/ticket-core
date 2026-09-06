package com.ticket.catalog.domain.performance.policy;

import com.ticket.catalog.domain.performance.query.PerformanceBookingPolicySnapshot;
import com.ticket.catalog.domain.queue.QueueMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오픈·마감과 좌석 수 한도 판정({@code ensureBookingOpen}/{@code ensureWithinHoldLimit})은 프로덕션에서
 * 아무도 호출하지 않아 제거했고, 그 테스트도 함께 지웠다. 같은 규칙은 booking의
 * {@code BookingPolicyGuard}가 소유하며 {@code BookingPolicyGuardTest}가 고정한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class BookingPolicyValidatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);

    @Test
    void 대기열_필요_여부는_정책의_대기열_설정을_따른다() {
        assertThat(BookingPolicyValidator.requiresQueue(queuePolicy(QueueMode.FORCE_ON), NOW)).isTrue();
        assertThat(BookingPolicyValidator.requiresQueue(queuePolicy(QueueMode.FORCE_OFF), NOW)).isFalse();
        assertThat(BookingPolicyValidator.requiresQueue(queuePolicy(null), NOW)).isFalse();
    }

    private PerformanceBookingPolicySnapshot queuePolicy(final QueueMode queueMode) {
        return new PerformanceBookingPolicySnapshot(
                10L, 1L, NOW.minusHours(1), NOW.plusHours(1), 4, 300, queueMode, null, null, null, null);
    }
}
