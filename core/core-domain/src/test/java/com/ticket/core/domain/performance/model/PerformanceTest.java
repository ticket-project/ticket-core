package com.ticket.core.domain.performance.model;

import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
    void 요청좌석수가_최대선점수보다_크면_초과다() {
        //given
        //when
        Performance performance = createPerformance(3, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        //then
        assertThat(performance.isOverCount(4)).isTrue();
    }

    @Test
    void 요청좌석수가_최대선점수와_같으면_초과가_아니다() {
        //given
        //when
        Performance performance = createPerformance(3, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        //then
        assertThat(performance.isOverCount(3)).isFalse();
    }

    @Test
    void treats_null_hold_limit_as_unlimited() {
        Performance performance = createPerformance(null, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        assertThat(performance.isOverCount(999)).isFalse();
    }

    @Test
    void rejects_hold_limit_less_than_two() {
        assertThatThrownBy(() -> createPerformance(1, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10)))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));

        assertThatThrownBy(() -> createPerformance(0, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10)))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType()).isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void accepts_hold_limit_of_two_as_minimum_boundary() {
        Performance performance = createPerformance(2, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        assertThat(performance.isOverCount(2)).isFalse();
        assertThat(performance.isOverCount(3)).isTrue();
    }

    @Test
    void 오픈시간과_마감시간_사이면_예매가능하다() {
        //given
        //when
        Performance performance = createPerformance(3, LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(10));

        //then
        assertThat(performance.isBookingOpen(LocalDateTime.now())).isTrue();
    }

    @Test
    void 오픈시각과_같으면_예매가능하다() {
        //given
        //when
        LocalDateTime now = LocalDateTime.now();
        Performance performance = createPerformance(3, now, now.plusMinutes(10));

        //then
        assertThat(performance.isBookingOpen(now)).isTrue();
    }

    @Test
    void 마감시각과_같으면_예매가능하다() {
        //given
        //when
        LocalDateTime now = LocalDateTime.now();
        Performance performance = createPerformance(3, now.minusMinutes(10), now);

        //then
        assertThat(performance.isBookingOpen(now)).isTrue();
    }

    @Test
    void 오픈시간이_없으면_예매불가다() {
        //given
        //when
        Performance performance = createPerformance(3, null, LocalDateTime.now().plusMinutes(10));

        //then
        assertThat(performance.isBookingOpen(LocalDateTime.now())).isFalse();
    }

    @Test
    void 오픈전이면_예매불가다() {
        //given
        //when
        Performance performance = createPerformance(3, LocalDateTime.now().plusMinutes(10), LocalDateTime.now().plusMinutes(20));

        //then
        assertThat(performance.isBookingOpen(LocalDateTime.now())).isFalse();
    }

    @Test
    void 마감후면_예매불가다() {
        //given
        //when
        Performance performance = createPerformance(3, LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(10));

        //then
        assertThat(performance.isBookingOpen(LocalDateTime.now())).isFalse();
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
        assertThat(performance.getQueueMode()).isEqualTo(QueueMode.FORCE_ON);
        assertThat(performance.getQueueLevel()).isEqualTo(QueueLevel.LEVEL_2);
        assertThat(performance.getPreopenQueueStartAt()).isEqualTo(preopen);
        assertThat(performance.getWaitingRoomMessage()).isEqualTo("대기열 운영");
        assertThat(performance.getReason()).isEqualTo("초기 정책");
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

