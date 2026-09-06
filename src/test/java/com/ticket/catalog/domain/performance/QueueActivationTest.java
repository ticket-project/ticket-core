package com.ticket.catalog.domain.performance;

import com.ticket.catalog.domain.queue.QueueMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class QueueActivationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);
    private static final LocalDateTime CLOSE = NOW.plusHours(1);

    @Test
    void queue_mode가_없으면_대기열을_요구하지_않는다() {
        assertThat(QueueActivation.isRequiredAt(null, NOW.minusMinutes(5), NOW, CLOSE)).isFalse();
    }

    @Test
    void force_off면_대기열을_요구하지_않는다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.FORCE_OFF, NOW.minusMinutes(5), NOW, CLOSE)).isFalse();
    }

    @Test
    void force_on이면_시각과_무관하게_대기열을_요구한다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.FORCE_ON, null, NOW, null)).isTrue();
        assertThat(QueueActivation.isRequiredAt(QueueMode.FORCE_ON, null, NOW, NOW.minusHours(1))).isTrue();
    }

    @Test
    void auto는_preopen_시각_이후부터_대기열을_요구한다() {
        LocalDateTime preopen = NOW.minusMinutes(5);

        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, preopen, NOW.minusMinutes(10), CLOSE)).isFalse();
        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, preopen, NOW, CLOSE)).isTrue();
    }

    @Test
    void auto는_preopen_시각과_같으면_대기열을_요구한다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, NOW, NOW, CLOSE)).isTrue();
    }

    @Test
    void auto는_preopen_시각이_없으면_대기열을_요구하지_않는다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, null, NOW, CLOSE)).isFalse();
    }

    @Test
    void auto는_now가_없으면_대기열을_요구하지_않는다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, NOW.minusMinutes(5), null, CLOSE)).isFalse();
    }

    @Test
    void auto는_마감_이후에는_대기열을_요구하지_않는다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, NOW.minusHours(3), NOW, NOW.minusHours(1))).isFalse();
    }

    @Test
    void auto는_마감_시각과_같으면_아직_대기열을_요구한다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, NOW.minusHours(1), NOW, NOW)).isTrue();
    }

    @Test
    void auto는_마감_시각이_없으면_preopen_이후_계속_대기열을_요구한다() {
        assertThat(QueueActivation.isRequiredAt(QueueMode.AUTO, NOW.minusMinutes(5), NOW, null)).isTrue();
    }
}
