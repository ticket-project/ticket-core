package com.ticket.core.infra.performance.query;

import com.ticket.core.app.performance.query.PerformanceReadRepository;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.app.performance.query.model.PerformanceSummaryView;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.venue.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslPerformanceReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslPerformanceReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private PerformanceReadRepository repository;

    @Test
    void 회차와_공연장_요약을_한번에_조회한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow(
                "싱어게인",
                venue,
                null,
                0L,
                LocalDateTime.now(clock).minusDays(1),
                LocalDateTime.now(clock).plusDays(1)
        );
        Performance performance = persistPerformance(show, 1L, LocalDateTime.now(clock).plusDays(1));
        Long performanceId = performance.getId();
        LocalDateTime startTime = performance.getStartTime();
        flushAndClear();

        PerformanceSummaryView result = repository.findByPerformanceId(performanceId).orElseThrow();

        assertThat(result.title()).isEqualTo("싱어게인");
        assertThat(result.region()).isEqualTo(Region.SEOUL);
        assertThat(result.startTime()).isEqualTo(startTime);
        assertThat(result.maxCanHoldCount()).isEqualTo(4);
    }
}
