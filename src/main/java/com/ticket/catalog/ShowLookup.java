package com.ticket.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 다른 module이 show 존재 확인과 표시값 조합에 쓰는 공개 계약이다. JPA entity를 노출하지 않는다.
 */
public interface ShowLookup {

    /**
     * show가 존재하지 않으면 {@code com.ticket.core.support.exception.CoreException}
     * ({@code ErrorType.NOT_FOUND_DATA})을 던진다. 존재하면 아무 것도 하지 않는다.
     */
    void requireExisting(long showId);

    /**
     * 빈 {@code showIds}는 빈 map을 반환한다. 존재하지 않는 ID는 결과 map에서 조용히 빠진다 —
     * 어떤 ID가 없었는지 의미를 부여하는 것은 호출자의 몫이다.
     */
    Map<Long, ShowSummary> getSummaries(Set<Long> showIds);

    /**
     * show에 연결된 공연장의 좌석 맵 배치를 조회한다. show가 없거나 공연장이 연결되지 않았으면
     * {@code CoreException}({@code ErrorType.NOT_FOUND_DATA})을 던진다.
     */
    VenueLayout getVenueLayout(long showId);

    /**
     * show의 좌석 맵을 물리 좌석·등급·가격과 함께 조회한다. show가 존재하지 않으면
     * {@code CoreException}({@code ErrorType.NOT_FOUND_DATA})을 던진다. show는 있지만 연결된
     * 좌석이 없으면 빈 목록을 반환한다.
     */
    List<ShowSeatMapEntry> getSeatMap(long showId);

    /**
     * 빈 {@code performanceIds}는 빈 map을 반환한다. 존재하지 않는 ID는 결과 map에서 조용히
     * 빠진다 — 어떤 ID가 없었는지 의미를 부여하는 것은 호출자의 몫이다.
     */
    Map<Long, PerformanceSummary> getPerformanceSummaries(Set<Long> performanceIds);
}
