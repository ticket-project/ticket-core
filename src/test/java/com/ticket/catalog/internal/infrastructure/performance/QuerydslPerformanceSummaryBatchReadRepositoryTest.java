package com.ticket.catalog.internal.infrastructure.performance;

import com.ticket.catalog.PerformanceSummary;
import com.ticket.catalog.internal.application.performance.query.PerformanceSummaryBatchReadRepository;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.show.Region;
import com.ticket.catalog.internal.domain.show.Show;
import com.ticket.catalog.internal.domain.show.Venue;
import com.ticket.core.infra.support.InfraReadRepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Import(QuerydslPerformanceSummaryBatchReadRepository.class)
@SuppressWarnings("NonAsciiCharacters")
class QuerydslPerformanceSummaryBatchReadRepositoryTest extends InfraReadRepositoryTestSupport {

    @Autowired
    private PerformanceSummaryBatchReadRepository repository;

    @Test
    void 회차_표시값_배치를_조회한다() throws Exception {
        Venue venue = persistVenue("올림픽홀", Region.SEOUL);
        Show show = persistShow("뮤지컬", venue, null, 0L, LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock).plusDays(1));
        LocalDateTime startTime = LocalDateTime.now(clock).plusDays(1);
        Performance performance = persistPerformance(show, 3L, startTime);
        flushAndClear();

        Map<Long, PerformanceSummary> summaries = repository.findSummaries(Set.of(performance.getId()));

        assertThat(summaries).containsOnlyKeys(performance.getId());
        PerformanceSummary summary = summaries.get(performance.getId());
        assertThat(summary.showId()).isEqualTo(show.getId());
        assertThat(summary.performanceNo()).isEqualTo(3L);
        assertThat(summary.startTime()).isEqualTo(startTime);
    }

    @Test
    void 빈_ID_목록은_빈_map을_반환한다() {
        assertThat(repository.findSummaries(Set.of())).isEmpty();
    }
}
