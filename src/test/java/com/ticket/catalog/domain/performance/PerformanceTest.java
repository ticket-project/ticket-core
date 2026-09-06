package com.ticket.catalog.domain.performance;

import com.ticket.catalog.domain.queue.QueueLevel;
import com.ticket.catalog.domain.queue.QueueMode;
import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PerformanceTest {

    @Test
    void 기본_holdTime은_10분이다() throws Exception {
        Constructor<Performance> constructor = Performance.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        Performance performance = constructor.newInstance();

        assertThat(performance.getHoldTime()).isEqualTo(600);
    }

    @Test
    void rejects_hold_limit_less_than_two() {
        assertThatThrownBy(() -> createPerformance(1, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10)))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> createPerformance(0, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void accepts_hold_limit_of_two_as_minimum_boundary() {
        assertThatCode(() -> createPerformance(2, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10)))
                .doesNotThrowAnyException();
    }

    @Test
    void 한도가_없으면_그대로_둔다() {
        Performance performance = createPerformance(null, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        assertThat(performance.getMaxCanHoldCount()).isNull();
    }

    @Test
    void updateQueuePolicy는_대기열_정책값을_변경한다() {
        // given
        Performance performance = createPerformance(3, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));
        LocalDateTime preopen = LocalDateTime.of(2026, 3, 15, 19, 50);

        // when
        performance.updateQueuePolicy(
                QueueMode.FORCE_ON,
                QueueLevel.LEVEL_2,
                preopen,
                "대기열 운영",
                "초기 정책"
        );

        // then
        PerformanceQueuePolicy queuePolicy = performance.getQueuePolicy();
        assertThat(queuePolicy.getQueueMode()).isEqualTo(QueueMode.FORCE_ON);
        assertThat(queuePolicy.getQueueLevel()).isEqualTo(QueueLevel.LEVEL_2);
        assertThat(queuePolicy.getPreopenQueueStartAt()).isEqualTo(preopen);
        assertThat(queuePolicy.getWaitingRoomMessage()).isEqualTo("대기열 운영");
        assertThat(queuePolicy.getReason()).isEqualTo("초기 정책");
    }

    private Performance createPerformance(
            final Integer maxCanHoldCount,
            final LocalDateTime orderOpenTime,
            final LocalDateTime orderCloseTime
    ) {
        return new Performance(
                null,
                1L,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                orderOpenTime,
                orderCloseTime,
                maxCanHoldCount,
                300
        );
    }
}
