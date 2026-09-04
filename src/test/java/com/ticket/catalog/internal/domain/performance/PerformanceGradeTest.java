package com.ticket.catalog.internal.domain.performance;

import com.ticket.catalog.internal.domain.grade.Grade;
import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class PerformanceGradeTest {

    @Test
    void 가격이_0_이상이면_생성된다() throws Exception {
        Performance performance = newPerformance();
        Grade grade = Grade.of("VIP", "VIP석");

        PerformanceGrade performanceGrade = PerformanceGrade.assign(performance, grade, BigDecimal.ZERO, 1);

        assertThat(performanceGrade.getPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(performanceGrade.getSortOrder()).isEqualTo(1);
        assertThat(performanceGrade.getPerformance()).isEqualTo(performance);
        assertThat(performanceGrade.getGrade()).isEqualTo(grade);
    }

    @Test
    void 가격이_음수이면_거부한다() throws Exception {
        Performance performance = newPerformance();
        Grade grade = Grade.of("VIP", "VIP석");

        assertThatThrownBy(() -> PerformanceGrade.assign(performance, grade, BigDecimal.valueOf(-1), 1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 가격이_null이면_거부한다() throws Exception {
        Performance performance = newPerformance();
        Grade grade = Grade.of("VIP", "VIP석");

        assertThatThrownBy(() -> PerformanceGrade.assign(performance, grade, null, 1))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 같은_Grade라도_회차마다_다른_가격을_가질_수_있다() throws Exception {
        Performance performanceA = newPerformance();
        Performance performanceB = newPerformance();
        Grade grade = Grade.of("VIP", "VIP석");

        PerformanceGrade performanceGradeA = PerformanceGrade.assign(performanceA, grade, BigDecimal.valueOf(100_000), 1);
        PerformanceGrade performanceGradeB = PerformanceGrade.assign(performanceB, grade, BigDecimal.valueOf(120_000), 1);

        assertThat(performanceGradeA.getGrade()).isEqualTo(performanceGradeB.getGrade());
        assertThat(performanceGradeA.getPrice()).isNotEqualByComparingTo(performanceGradeB.getPrice());
    }

    private Performance newPerformance() throws Exception {
        Constructor<Performance> constructor = Performance.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
