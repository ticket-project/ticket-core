package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.query.model.PerformanceSummaryView;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSummaryUseCase {

    private final PerformanceSummaryQueryRepository performanceSummaryQueryRepository;

    public record Input(Long performanceId) {}

    public record Output(
            String title,
            String region,
            LocalDateTime startTime,
            Integer maxCanHoldCount
    ) {}

    public Output execute(final Input input) {
        final PerformanceSummaryView summary = performanceSummaryQueryRepository
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
