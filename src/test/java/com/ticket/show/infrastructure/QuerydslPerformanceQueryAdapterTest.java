package com.ticket.show.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.ticket.show.application.port.PerformanceQueryPort;
import com.ticket.show.application.query.PerformanceSummaryView;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.show.Show;
import com.ticket.testsupport.persistence.InfraReadRepositoryTestSupport;
import com.ticket.venue.api.Region;
import com.ticket.venue.domain.Venue;

@Import(QuerydslPerformanceQueryAdapter.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslPerformanceQueryAdapterTest extends InfraReadRepositoryTestSupport {
    @Autowired private PerformanceQueryPort repository;

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
}
