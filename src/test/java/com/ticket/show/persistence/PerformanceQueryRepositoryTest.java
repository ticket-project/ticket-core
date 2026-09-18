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

import com.ticket.show.api.PerformanceSaleSnapshot.GradeInfo;
import com.ticket.show.domain.Grade;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.show.Show;
import com.ticket.show.query.PerformanceSummaryView;
import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.domain.Venue;

/** 옛 {@code PerformanceQueryTest}와 {@code PerformanceGradeQueryTest}가 고정하던 동작이 그대로 들어 있다. */
@Import(PerformanceQueryRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class PerformanceQueryRepositoryTest extends InfraReadRepositoryTestSupport {
    @Autowired private PerformanceQueryRepository repository;

    @Test
    void 회차와_공연장_요약을_한번에_조회한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show =
                persistShow(
                        "싱어게인",
                        venue,
                        null,
                        0L,
                        LocalDateTime.now(clock).minusDays(1),
                        LocalDateTime.now(clock).plusDays(1));
        Performance performance =
                persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Long performanceId = performance.getId();
        LocalDateTime startTime = performance.getStartTime();
        flushAndClear();

        PerformanceSummaryView result = repository.findByPerformanceId(performanceId).orElseThrow();

        assertThat(result.title()).isEqualTo("싱어게인");
        assertThat(result.venueId()).isEqualTo(venue.getId());
        assertThat(result.startTime()).isEqualTo(startTime);
    }

    @Test
    void 회차의_grade_목록을_가격과_표시순서와_함께_반환한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show =
                persistShow(
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

        List<GradeInfo> result = repository.findPerformanceGrades(performanceId);

        assertThat(result)
                .extracting(GradeInfo::gradeCode, GradeInfo::gradeName, GradeInfo::sortOrder)
                .containsExactlyInAnyOrder(tuple("VIP", "VIP석", 1), tuple("R", "R석", 2));
        Map<String, BigDecimal> priceByGradeCode =
                result.stream().collect(Collectors.toMap(GradeInfo::gradeCode, GradeInfo::price));
        assertThat(priceByGradeCode.get("VIP")).isEqualByComparingTo(BigDecimal.valueOf(150_000));
        assertThat(priceByGradeCode.get("R")).isEqualByComparingTo(BigDecimal.valueOf(80_000));
    }

    @Test
    void 연결된_grade가_없으면_빈_목록을_반환한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show =
                persistShow(
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
}
