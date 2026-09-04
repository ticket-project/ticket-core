package com.ticket.catalog.internal.infrastructure.performance;

import com.ticket.catalog.internal.domain.grade.Grade;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.performance.PerformanceGrade;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceGradeRepository;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import com.ticket.catalog.internal.infrastructure.grade.GradeRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({PerformanceGradeRepositoryAdapter.class, GradeRepositoryAdapter.class})
@SuppressWarnings("NonAsciiCharacters")
class PerformanceGradeRepositoryAdapterTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private PerformanceGradeRepository repository;

    @Test
    void 같은_Grade를_여러_Performance가_재사용할_수_있다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow("싱어게인", venue, null, 0L,
                LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock).plusDays(1));
        Performance performanceA = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Performance performanceB = persistPerformance(show, 2L, LocalDateTime.now(clock).plusDays(2));
        Grade grade = persistGrade("VIP", "VIP석");

        persistPerformanceGrade(performanceA, grade, BigDecimal.valueOf(100_000), 1);
        persistPerformanceGrade(performanceB, grade, BigDecimal.valueOf(120_000), 1);
        flushAndClear();

        List<PerformanceGrade> resultA = repository.findAllByPerformanceIdOrderBySortOrderAsc(performanceA.getId());
        List<PerformanceGrade> resultB = repository.findAllByPerformanceIdOrderBySortOrderAsc(performanceB.getId());

        assertThat(resultA).hasSize(1);
        assertThat(resultB).hasSize(1);
        assertThat(resultA.get(0).getGrade().getCode()).isEqualTo("VIP");
        assertThat(resultB.get(0).getGrade().getCode()).isEqualTo("VIP");
        assertThat(resultA.get(0).getPrice()).isNotEqualByComparingTo(resultB.get(0).getPrice());
    }

    @Test
    void 같은_Performance에_같은_Grade가_중복되면_제약을_위반한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow("싱어게인", venue, null, 0L,
                LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock).plusDays(1));
        Performance performance = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Grade grade = persistGrade("VIP", "VIP석");
        persistPerformanceGrade(performance, grade, BigDecimal.valueOf(100_000), 1);
        entityManager.flush();

        // GenerationType.IDENTITY는 즉시 INSERT하므로 예외가 flush가 아니라 persist 시점에 난다.
        assertThatThrownBy(() -> persistPerformanceGrade(performance, grade, BigDecimal.valueOf(150_000), 2))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void existsByPerformanceIdAndGradeId는_연결_존재_여부만_반환한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow("싱어게인", venue, null, 0L,
                LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock).plusDays(1));
        Performance performance = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Grade grade = persistGrade("VIP", "VIP석");
        persistPerformanceGrade(performance, grade, BigDecimal.valueOf(100_000), 1);
        flushAndClear();

        assertThat(repository.existsByPerformanceIdAndGradeId(performance.getId(), grade.getId())).isTrue();
        assertThat(repository.existsByPerformanceIdAndGradeId(performance.getId(), -1L)).isFalse();
    }
}
