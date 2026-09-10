package com.ticket.show.performance.application.usecase;

import com.ticket.show.performance.application.port.PerformanceQueryPort;

import com.ticket.show.performance.application.PerformanceSummaryView;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.venue.VenueLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSummaryUseCase {

    private final PerformanceQueryPort performanceQueryPort;
    private final VenueLookup venueLookup;

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
            String title,
            String region,
            LocalDateTime startTime
    ) {}

    public Output execute(final Input input) {
        final PerformanceSummaryView summary = performanceQueryPort
                .findByPerformanceId(input.performanceId())
                .orElseThrow(() -> new NotFoundException(
                                                "회차에 연결된 공연을 찾을 수 없습니다. id=" + input.performanceId()
                ));

        final String region = summary.venueId() == null
                ? null
                : venueLookup.findSummary(summary.venueId())
                        .map(v -> v.region().getDescription())
                        .orElse(null);

        return new Output(
                summary.title(),
                region,
                summary.startTime()
        );
    }
}
