package com.ticket.show.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.ticket.show.domain.Grade;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceGrade;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.Show;
import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.domain.Venue;

/** 옛 {@code PerformanceQueryTest}와 {@code PerformanceGradeQueryTest}가 고정하던 동작이 그대로 들어 있다. */
@Import(PerformanceRepositoryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceRepositoryTest extends InfraReadRepositoryTestSupport {
    @Autowired
    private PerformanceRepository repository;

    @Test
    void 회차의_grade_목록을_가격과_표시순서와_함께_반환한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow(
                "싱어게인",
                venue,
                null,
                0L,
                LocalDateTime.now(clock).minusDays(1),
                LocalDateTime.now(clock).plusDays(1));
        Performance performance =
                persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Grade vip = persistGrade("VIP", "VIP석");
        Grade r = persistGrade("R", "R석");
        persistPerformanceGrade(performance, r, BigDecimal.valueOf(80_000), 2);
        persistPerformanceGrade(performance, vip, BigDecimal.valueOf(150_000), 1);
        Long performanceId = performance.getId();
        flushAndClear();

        List<PerformanceGrade> result = repository.findPerformanceGrades(performanceId);

        assertThat(result)
                .extracting(PerformanceGrade::getGradeId, PerformanceGrade::getSortOrder)
                .containsExactlyInAnyOrder(tuple(vip.getId(), 1), tuple(r.getId(), 2));
        Map<Long, BigDecimal> priceByGradeId =
                result.stream().collect(Collectors.toMap(PerformanceGrade::getGradeId, PerformanceGrade::getPrice));
        assertThat(priceByGradeId.get(vip.getId())).isEqualByComparingTo(BigDecimal.valueOf(150_000));
        assertThat(priceByGradeId.get(r.getId())).isEqualByComparingTo(BigDecimal.valueOf(80_000));
    }

    @Test
    void 연결된_grade가_없으면_빈_목록을_반환한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow(
                "싱어게인",
                venue,
                null,
                0L,
                LocalDateTime.now(clock).minusDays(1),
                LocalDateTime.now(clock).plusDays(1));
        Performance performance =
                persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Long performanceId = performance.getId();
        flushAndClear();

        assertThat(repository.findPerformanceGrades(performanceId)).isEmpty();
    }

    /**
     * 옛 조회는 {@code join grade}가 inner join이라 Grade가 없는 편성을 조용히 빠뜨렸다. 이제 조회는 편성을 전부 돌려주고, 그 걸러내기는 등급 이름을 조합하는 use case가
     * 한다({@code PerformanceSaleCatalogService}).
     */
    @Test
    void 등급이_사라진_편성도_조회는_그대로_돌려준다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow(
                "싱어게인",
                venue,
                null,
                0L,
                LocalDateTime.now(clock).minusDays(1),
                LocalDateTime.now(clock).plusDays(1));
        Performance performance =
                persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Grade vip = persistGrade("VIP", "VIP석");
        persistPerformanceGrade(performance, vip, BigDecimal.valueOf(150_000), 1);
        PerformanceGrade dangling = PerformanceGrade.assign(performance, 999_999L, BigDecimal.valueOf(80_000), 2);
        entityManager.persist(dangling);
        Long performanceId = performance.getId();
        flushAndClear();

        assertThat(repository.findPerformanceGrades(performanceId))
                .extracting(PerformanceGrade::getGradeId)
                .containsExactlyInAnyOrder(vip.getId(), 999_999L);
    }
}
