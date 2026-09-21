package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.domain.performance.Performance;
import com.ticket.show.domain.performance.PerformanceRepository;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSummaryUseCase {
    private final PerformanceRepository performanceRepository;
    private final ShowRepository showRepository;
    private final VenueLookupApi venueLookup;

    public record Input(Long performanceId) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
        }
    }

    public record Output(
            @Nullable String title, @Nullable String region, @Nullable LocalDateTime startTime) {}

    public Output execute(final Input input) {
        final Performance performance =
                performanceRepository
                        .findById(input.performanceId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "회차에 연결된 공연을 찾을 수 없습니다. id="
                                                        + input.performanceId()));
        final Show show =
                showRepository
                        .findById(performance.getShowId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "회차에 연결된 공연을 찾을 수 없습니다. id="
                                                        + input.performanceId()));

        final String region =
                show.getVenueId() == null
                        ? null
                        : venueLookup
                                .findSummary(show.getVenueId())
                                .map(v -> v.region() == null ? null : v.region().getDescription())
                                .orElse(null);

        return new Output(show.getTitle(), region, performance.getStartTime());
    }
}
