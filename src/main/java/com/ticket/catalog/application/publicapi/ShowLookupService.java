package com.ticket.catalog.application.publicapi;

import com.ticket.catalog.PerformanceSummary;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSummary;
import com.ticket.catalog.VenueLayout;
import com.ticket.catalog.application.performance.query.PerformanceSummaryBatchReadRepository;
import com.ticket.catalog.application.show.query.ShowSummaryBatchReadRepository;
import com.ticket.catalog.domain.show.Show;
import com.ticket.catalog.domain.show.Venue;
import com.ticket.catalog.domain.show.repository.ShowRepository;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ShowLookup}의 catalog 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowLookupService implements ShowLookup {

    private final ShowRepository showRepository;
    private final ShowSummaryBatchReadRepository showSummaryBatchReadRepository;
    private final PerformanceSummaryBatchReadRepository performanceSummaryBatchReadRepository;

    @Override
    public void requireExisting(final long showId) {
        if (!showRepository.existsById(showId)) {
            throw new NotFoundException("공연을 찾을 수 없습니다. id=" + showId);
        }
    }

    @Override
    public Map<Long, ShowSummary> getSummaries(final Set<Long> showIds) {
        return showSummaryBatchReadRepository.findSummaries(showIds);
    }

    @Override
    public VenueLayout getVenueLayout(final long showId) {
        final Show show = showRepository.findById(showId)
                .orElseThrow(() -> new NotFoundException(
                        "공연을 찾을 수 없습니다. id=" + showId));
        final Venue venue = show.getVenue();
        if (venue == null) {
            throw new NotFoundException("공연에 연결된 공연장을 찾을 수 없습니다.");
        }
        return new VenueLayout(venue.getName(), venue.getViewBoxWidth(), venue.getViewBoxHeight(), venue.getSeatDiameter());
    }

    @Override
    public Map<Long, PerformanceSummary> getPerformanceSummaries(final Set<Long> performanceIds) {
        return performanceSummaryBatchReadRepository.findSummaries(performanceIds);
    }
}
