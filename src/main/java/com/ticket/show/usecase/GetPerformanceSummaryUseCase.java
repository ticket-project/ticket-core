package com.ticket.show.usecase;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.query.PerformanceQuery;
import com.ticket.show.query.PerformanceSummaryView;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSummaryUseCase {
    private final PerformanceQuery performanceQuery;
    private final VenueLookupApi venueLookup;

    public record Input(Long performanceId) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
        }
    }

    public record Output(
            @Nullable String title, @Nullable String region, @Nullable LocalDateTime startTime) {}

    public Output execute(final Input input) {
        final PerformanceSummaryView summary =
                performanceQuery
                        .findByPerformanceId(input.performanceId())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "회차에 연결된 공연을 찾을 수 없습니다. id="
                                                        + input.performanceId()));

        final String region =
                summary.venueId() == null
                        ? null
                        : venueLookup
                                .findSummary(summary.venueId())
                                .map(v -> v.region() == null ? null : v.region().getDescription())
                                .orElse(null);

        return new Output(summary.title(), region, summary.startTime());
    }
}
