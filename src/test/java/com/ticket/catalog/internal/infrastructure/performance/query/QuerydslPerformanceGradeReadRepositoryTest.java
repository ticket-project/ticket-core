package com.ticket.catalog.internal.infrastructure.performance.query;

import com.ticket.catalog.internal.application.performance.query.PerformanceGradeReadRepository;
import com.ticket.catalog.internal.application.performance.query.model.PerformanceGradeView;
import com.ticket.catalog.internal.domain.grade.Grade;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslPerformanceGradeReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslPerformanceGradeReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private PerformanceGradeReadRepository repository;

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
