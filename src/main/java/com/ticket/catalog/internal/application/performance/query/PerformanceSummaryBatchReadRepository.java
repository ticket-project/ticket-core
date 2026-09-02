package com.ticket.catalog.internal.application.performance.query;

import com.ticket.catalog.PerformanceSummary;

import java.util.Map;
import java.util.Set;

/**
 * {@link com.ticket.catalog.ShowLookup#getPerformanceSummaries(Set)}가 쓰는 batch 표시값 조회
 * 포트다.
 */
public interface PerformanceSummaryBatchReadRepository {

    /**
     * 빈 {@code performanceIds}는 빈 map을 반환한다.
     */
    Map<Long, PerformanceSummary> findSummaries(Set<Long> performanceIds);
}
