package com.ticket.show.performance.infrastructure;

import com.ticket.show.performance.application.port.PerformanceGradeQueryPort;

import com.ticket.show.performance.application.port.PerformanceGradeQueryPort;
import com.ticket.show.performance.application.PerformanceGradeView;
import com.ticket.show.performance.domain.Grade;
import com.ticket.show.performance.domain.Performance;
import com.ticket.venue.Region;
import com.ticket.show.catalog.domain.Show;
import com.ticket.venue.domain.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslPerformanceGradeQueryPort.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslPerformanceGradeQueryPortTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private PerformanceGradeQueryPort repository;

    @Test
    void 회차의_grade_목록을_표시_순서대로_가격과_함께_반환한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow("싱어게인", venue, null, 0L,
                LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock).plusDays(1));
        Performance performance = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Grade vip = persistGrade("VIP", "VIP석");
        Grade r = persistGrade("R", "R석");
        persistPerformanceGrade(performance, r, BigDecimal.valueOf(80_000), 2);
        persistPerformanceGrade(performance, vip, BigDecimal.valueOf(150_000), 1);
        Long performanceId = performance.getId();
        flushAndClear();

        List<PerformanceGradeView> result = repository.findAllByPerformanceIdOrderBySortOrderAsc(performanceId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).gradeCode()).isEqualTo("VIP");
        assertThat(result.get(0).price()).isEqualByComparingTo(BigDecimal.valueOf(150_000));
        assertThat(result.get(1).gradeCode()).isEqualTo("R");
        assertThat(result.get(1).price()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
    }

    @Test
    void 연결된_grade가_없으면_빈_목록을_반환한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow("싱어게인", venue, null, 0L,
                LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock).plusDays(1));
        Performance performance = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Long performanceId = performance.getId();
        flushAndClear();

        List<PerformanceGradeView> result = repository.findAllByPerformanceIdOrderBySortOrderAsc(performanceId);

        assertThat(result).isEmpty();
    }
}
