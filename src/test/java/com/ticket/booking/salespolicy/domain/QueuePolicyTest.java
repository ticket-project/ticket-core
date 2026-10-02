package com.ticket.booking.salespolicy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * 옛 {@code show.domain.performance.QueueActivation}의 판정 규칙을 이 aggregate로 옮겨 온 테스트다. now·마감 시각이 null인 케이스는 뺐다 — 호출자가 현재
 * 시각과 not null 열인 마감 시각을 넘기므로 Core에서 생기지 않는다.
 */
@SuppressWarnings("NonAsciiCharacters")
class QueuePolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 10, 0);
    private static final LocalDateTime CLOSE = NOW.plusHours(1);

    @Test
    void queue_mode가_없으면_대기열을_요구하지_않는다() {
        QueuePolicy policy = new QueuePolicy(null, NOW.minusMinutes(5));

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }

    @Test
    void force_off면_대기열을_요구하지_않는다() {
        QueuePolicy policy = new QueuePolicy(QueueMode.FORCE_OFF, NOW.minusMinutes(5));

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }

    @Test
    void force_on이면_시각과_무관하게_대기열을_요구한다() {
        assertThat(new QueuePolicy(QueueMode.FORCE_ON, null).isRequiredAt(NOW, CLOSE))
                .isTrue();
        assertThat(new QueuePolicy(QueueMode.FORCE_ON, null).isRequiredAt(NOW, NOW.minusHours(1)))
                .isTrue();
    }

    @Test
    void auto는_preopen_시각_이후부터_대기열을_요구한다() {
        LocalDateTime preopen = NOW.minusMinutes(5);
        QueuePolicy policy = new QueuePolicy(QueueMode.AUTO, preopen);

        assertThat(policy.isRequiredAt(NOW.minusMinutes(10), CLOSE)).isFalse();
        assertThat(policy.isRequiredAt(NOW, CLOSE)).isTrue();
    }

    @Test
    void auto는_preopen_시각과_같으면_대기열을_요구한다() {
        QueuePolicy policy = new QueuePolicy(QueueMode.AUTO, NOW);

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isTrue();
    }

    @Test
    void auto는_preopen_시각이_없으면_대기열을_요구하지_않는다() {
        QueuePolicy policy = new QueuePolicy(QueueMode.AUTO, null);

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }

    @Test
    void auto는_마감_이후에는_대기열을_요구하지_않는다() {
        QueuePolicy policy = new QueuePolicy(QueueMode.AUTO, NOW.minusHours(3));

        assertThat(policy.isRequiredAt(NOW, NOW.minusHours(1))).isFalse();
    }

    @Test
    void auto는_마감_시각과_같으면_아직_대기열을_요구한다() {
        QueuePolicy policy = new QueuePolicy(QueueMode.AUTO, NOW.minusHours(1));

        assertThat(policy.isRequiredAt(NOW, NOW)).isTrue();
    }

    @Test
    void none은_queue_mode가_없는_정책이다() {
        QueuePolicy policy = QueuePolicy.none();

        assertThat(policy.isRequiredAt(NOW, CLOSE)).isFalse();
    }
}
