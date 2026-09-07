package com.ticket.venue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 다른 module이 venue 존재 확인과 표시값 조합에 쓰는 공개 계약이다. JPA entity를 노출하지 않는다.
 */
public interface VenueLookup {

    /**
     * 존재하지 않으면 empty를 반환한다 — 예외를 던지지 않는다. 없는 venueId를 어떤 오류로 볼지는
     * 호출하는 module이 정한다.
     */
    Optional<VenueSummary> findSummary(long venueId);

    /**
     * 빈 {@code venueIds}는 빈 map을 반환한다. 존재하지 않는 ID는 결과 map에서 조용히 빠진다 —
     * 어떤 ID가 없었는지 의미를 부여하는 것은 호출자의 몫이다. 쿼리 1회로 처리한다.
     */
    Map<Long, VenueSummary> getSummaries(Set<Long> venueIds);

    /**
     * 주어진 지역에 속한 venue의 id 집합을 반환한다. 해당 지역에 venue가 없으면 빈 집합이다.
     *
     * @throws IllegalArgumentException region이 null이면 던진다 — "필터 없음"은 호출자가 이
     *                                   메서드를 부르지 않는 것으로 표현한다.
     */
    Set<Long> findIdsByRegion(Region region);
}
