package com.ticket.show.performance.domain;

import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PerformanceGradeTest {

    private static final Long VIP_GRADE_ID = 1L;

    @Test
    void 가격이_0_이상이면_생성된다() throws Exception {
        Performance performance = newPerformance();

        PerformanceGrade performanceGrade = PerformanceGrade.assign(performance, VIP_GRADE_ID, BigDecimal.ZERO, 1);

        assertThat(performanceGrade.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(performanceGrade.getSortOrder()).isEqualTo(1);
        assertThat(performanceGrade.getPerformance()).isEqualTo(performance);
        assertThat(performanceGrade.getGradeId()).isEqualTo(VIP_GRADE_ID);
    }

    @Test
    void 가격이_음수이면_거부한다() throws Exception {
        Performance performance = newPerformance();

        assertThatThrownBy(() -> PerformanceGrade.assign(performance, VIP_GRADE_ID, BigDecimal.valueOf(-1), 1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 가격이_null이면_거부한다() throws Exception {
        Performance performance = newPerformance();

        assertThatThrownBy(() -> PerformanceGrade.assign(performance, VIP_GRADE_ID, null, 1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 같은_Grade라도_회차마다_다른_가격을_가질_수_있다() throws Exception {
        Performance performanceA = newPerformance();
        Performance performanceB = newPerformance();

        PerformanceGrade performanceGradeA = PerformanceGrade.assign(performanceA, VIP_GRADE_ID, BigDecimal.valueOf(100_000), 1);
        PerformanceGrade performanceGradeB = PerformanceGrade.assign(performanceB, VIP_GRADE_ID, BigDecimal.valueOf(120_000), 1);

        assertThat(performanceGradeA.getGradeId()).isEqualTo(performanceGradeB.getGradeId());
        assertThat(performanceGradeA.getPrice()).isNotEqualByComparingTo(performanceGradeB.getPrice());
    }

    private Performance newPerformance() throws Exception {
        Constructor<Performance> constructor = Performance.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
