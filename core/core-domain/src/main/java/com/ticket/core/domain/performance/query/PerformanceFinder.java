package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.repository.PerformanceRepository;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PerformanceFinder {

    private final PerformanceRepository performanceRepository;

    public Performance findOpenPerformance(final Long performanceId, final LocalDateTime now) {
        final Performance performance = findById(performanceId);
        if (!performance.isBookingOpen(now)) {
            throw new NotFoundException(ErrorType.NOT_FOUND_DATA);
        }
        return performance;
    }

    public Performance findById(final Long performanceId) {
        return performanceRepository.findWithQueuePolicyById(performanceId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA, "공연을 찾을 수 없습니다. id=" + performanceId));
    }

    public Performance findValidPerformanceById(final Long performanceId, final LocalDateTime now) {
        final Performance performance = findById(performanceId);
        validatePerformance(performance, now);
        return performance;
    }

    private void validatePerformance(final Performance performance, final LocalDateTime now) {
        if (performance.getOrderOpenTime() == null || now.isBefore(performance.getOrderOpenTime())) {
            throw new CoreException(ErrorType.NOT_YET_RESERVE_TIME);
        }
        if (performance.getOrderCloseTime() == null || now.isAfter(performance.getOrderCloseTime())) {
            throw new CoreException(ErrorType.PERFORMANCE_IS_PAST);
        }
    }
}
