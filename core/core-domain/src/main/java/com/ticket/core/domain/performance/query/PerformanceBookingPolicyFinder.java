package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PerformanceBookingPolicyFinder {

    private final PerformanceBookingPolicyQueryRepository queryRepository;

    public PerformanceBookingPolicyView findById(final Long performanceId) {
        return queryRepository.findByPerformanceId(performanceId)
                .orElseThrow(() -> new CoreException(
                        ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + performanceId
                ));
    }

    public PerformanceBookingPolicyView findValidById(
            final Long performanceId,
            final LocalDateTime now
    ) {
        final PerformanceBookingPolicyView policy = findById(performanceId);
        if (policy.orderOpenTime() == null || now.isBefore(policy.orderOpenTime())) {
            throw new CoreException(ErrorType.NOT_YET_RESERVE_TIME);
        }
        if (policy.orderCloseTime() == null || now.isAfter(policy.orderCloseTime())) {
            throw new CoreException(ErrorType.PERFORMANCE_IS_PAST);
        }
        return policy;
    }
}
