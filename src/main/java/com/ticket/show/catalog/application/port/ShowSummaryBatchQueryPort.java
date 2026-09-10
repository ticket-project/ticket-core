package com.ticket.show.catalog.application.port;

import com.ticket.show.catalog.application.ShowSummaryRow;

import java.util.Map;
import java.util.Set;

/**
 * showId 집합으로 공연 표시값을 배치 조회하는 내부 포트다. 내 찜 목록처럼 show 내부의 다른
 * use case가 자기 show 데이터를 조회할 때 쓴다.
 */
public interface ShowSummaryBatchQueryPort {

    /**
     * 빈 {@code showIds}는 빈 map을 반환한다.
     */
    Map<Long, ShowSummaryRow> findSummaries(Set<Long> showIds);
}
