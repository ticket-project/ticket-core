package com.ticket.core.domain.performance.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PerformanceFinder {

    private final PerformanceRepository performanceRepository;

    public Performance findById(final Long performanceId) {
        return performanceRepository.findWithQueuePolicyById(performanceId)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND, "공연을 찾을 수 없습니다. id=" + performanceId));
    }
}
