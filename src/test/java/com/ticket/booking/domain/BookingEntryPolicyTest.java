package com.ticket.booking.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 옛 {@code show.domain.performance.QueueActivation}의 판정 규칙을 이 aggregate로 옮겨 온
 * 테스트다 — 케이스는 그대로 보존한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class BookingEntryPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);
    private static final LocalDateTime CLOSE = NOW.plusHours(1);

    @Test
    void queue_mode가_없으면_대기열을_요구하지_않는다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(null, null, NOW.minusMinutes(5), null, null);

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }

    @Test
    void force_off면_대기열을_요구하지_않는다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.FORCE_OFF, null, NOW.minusMinutes(5), null, null);

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }

    @Test
    void force_on이면_시각과_무관하게_대기열을_요구한다() {
        assertThat(new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null).isRequiredAt(NOW, null)).isTrue();
        assertThat(new BookingEntryPolicy(QueueMode.FORCE_ON, null, null, null, null).isRequiredAt(NOW, NOW.minusHours(1))).isTrue();
    }

    @Test
    void auto는_preopen_시각_이후부터_대기열을_요구한다() {
        LocalDateTime preopen = NOW.minusMinutes(5);
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.AUTO, null, preopen, null, null);

        assertThat(policy.isRequiredAt(NOW.minusMinutes(10), CLOSE)).isFalse();
        assertThat(policy.isRequiredAt(NOW, CLOSE)).isTrue();
    }

    @Test
    void auto는_preopen_시각과_같으면_대기열을_요구한다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.AUTO, null, NOW, null, null);

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isTrue();
    }

    @Test
    void auto는_preopen_시각이_없으면_대기열을_요구하지_않는다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.AUTO, null, null, null, null);

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }

    @Test
    void auto는_now가_없으면_대기열을_요구하지_않는다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.AUTO, null, NOW.minusMinutes(5), null, null);

        assertThat(policy.isRequiredAt(null, CLOSE)).isFalse();
    }

    @Test
    void auto는_마감_이후에는_대기열을_요구하지_않는다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.AUTO, null, NOW.minusHours(3), null, null);

        assertThat(policy.isRequiredAt(NOW, NOW.minusHours(1))).isFalse();
    }

    @Test
    void auto는_마감_시각과_같으면_아직_대기열을_요구한다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.AUTO, null, NOW.minusHours(1), null, null);

        assertThat(policy.isRequiredAt(NOW, NOW)).isTrue();
    }

    @Test
    void auto는_마감_시각이_없으면_preopen_이후_계속_대기열을_요구한다() {
        BookingEntryPolicy policy = new BookingEntryPolicy(QueueMode.AUTO, null, NOW.minusMinutes(5), null, null);

        assertThat(policy.isRequiredAt(NOW, null)).isTrue();
    }

    @Test
    void none은_queue_mode가_없는_정책이다() {
        BookingEntryPolicy policy = BookingEntryPolicy.none();

        assertThat(policy.queueMode()).isNull();
        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }
}
