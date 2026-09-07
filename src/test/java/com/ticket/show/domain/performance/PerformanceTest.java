package com.ticket.show.domain.performance;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance는 이제 회차 일정 책임만 갖는다. 예매 정책·Hold 한도·대기열 정책 테스트는
 * {@code booking.domain.performancepolicy.model} 아래로 이관됐다(ADR 0006 "Performance의 책임 혼재" A2).
 */
@SuppressWarnings("NonAsciiCharacters")
class PerformanceTest {

    @Test
    void 회차_정체성과_일정을_그대로_보관한다() {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = startTime.plusHours(2);

        Performance performance = new Performance(null, 1L, startTime, endTime);

        assertThat(performance.getPerformanceNo()).isEqualTo(1L);
        assertThat(performance.getStartTime()).isEqualTo(startTime);
        assertThat(performance.getEndTime()).isEqualTo(endTime);
    }
}
