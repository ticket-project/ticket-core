package com.ticket.core.app.performance.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.app.performance.query.model.PerformanceSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSummaryUseCase {

    private final PerformanceReadRepository performanceReadRepository;

    public record Input(Long performanceId) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
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
                .orElseThrow(() -> new CoreException(
                        ErrorType.NOT_FOUND_DATA,
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
