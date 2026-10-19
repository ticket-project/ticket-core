package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.show.exception.PerformanceNotFoundException;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSummaryUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookupApi;

    public record Input(Long performanceId) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
        }
    }

    public record Output(
            @Nullable String title,
            @Nullable String region,
            @Nullable LocalDateTime startTime) {}

    public Output execute(final Input input) {
        final PerformanceShowRow performance = showQuerydslRepository
                .findPerformanceShow(input.performanceId())
                .orElseThrow(() -> new PerformanceNotFoundException(input.performanceId()));

        final VenueSnapshot venue = venueLookupApi.getVenueSnapshot(performance.requireVenueId());
        final String region = venue.region() == null ? null : venue.region().name();

        return new Output(performance.title(), region, performance.startTime());
    }
}
