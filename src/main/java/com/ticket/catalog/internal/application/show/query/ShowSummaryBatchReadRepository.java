package com.ticket.catalog.internal.application.show.query;

import com.ticket.catalog.ShowSummary;

import java.util.Map;
import java.util.Set;

/**
 * {@link com.ticket.catalog.ShowLookup#getSummaries(Set)}가 쓰는 batch 표시값 조회 포트다.
 */
public interface ShowSummaryBatchReadRepository {

    /**
     * 빈 {@code showIds}는 빈 map을 반환한다.
     */
    Map<Long, ShowSummary> findSummaries(Set<Long> showIds);
}
