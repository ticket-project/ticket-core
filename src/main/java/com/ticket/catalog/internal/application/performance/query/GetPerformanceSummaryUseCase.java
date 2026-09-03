package com.ticket.catalog.internal.application.performance.query;

import com.ticket.catalog.internal.application.performance.query.model.PerformanceSummaryView;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSummaryUseCase {

    private final PerformanceReadRepository performanceReadRepository;

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
            LocalDateTime startTime,
            Integer maxCanHoldCount
    ) {}

    public Output execute(final Input input) {
        final PerformanceSummaryView summary = performanceReadRepository
                .findByPerformanceId(input.performanceId())
                .orElseThrow(() -> new NotFoundException(
                                                "회차에 연결된 공연을 찾을 수 없습니다. id=" + input.performanceId()
                ));

        return new Output(
                summary.title(),
                summary.region() != null ? summary.region().getDescription() : null,
                summary.startTime(),
                summary.maxCanHoldCount()
        );
    }
}
