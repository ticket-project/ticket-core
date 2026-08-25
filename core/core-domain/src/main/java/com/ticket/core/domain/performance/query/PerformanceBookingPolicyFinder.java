package com.ticket.core.domain.performance.query;

import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
