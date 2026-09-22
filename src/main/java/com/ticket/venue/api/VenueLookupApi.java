package com.ticket.venue.api;

import java.util.Map;
import java.util.Set;

/** 다른 module이 venue 존재 확인과 표시값 조합에 쓰는 공개 계약이다. JPA entity를 노출하지 않는다. */
public interface VenueLookupApi {
    /**
     * 단건 공연장 조회다. 없으면 venue가 not-found 오류를 던진다 — 오류 코드 {@code E404}와 HTTP 404로 나간다.
     *
     * <p>호출하는 module은 그 예외를 잡지 않는다. 공연장을 가리키는 id가 있는데 그 공연장이 없는 것은 정상 상태가 아니다. 여러 건을 한 번에 채우는 목록 조회만
     * {@link #getSummaries(Set)}로 없는 id를 조용히 건너뛴다 — 한 건 때문에 목록 전체를 실패시키지 않기 위해서다.
     */
    VenueSnapshot getVenueSnapshot(long venueId);

    /**
     * 빈 {@code venueIds}는 빈 map을 반환한다. 존재하지 않는 ID는 결과 map에서 조용히 빠진다 — 어떤 ID가 없었는지 의미를 부여하는 것은 호출자의 몫이다. 쿼리 1회로 처리한다.
     */
    Map<Long, VenueSnapshot> getSummaries(Set<Long> venueIds);

    /**
     * 주어진 지역 코드({@code "SEOUL"})에 속한 venue의 id 집합을 반환한다. 해당 지역에 venue가 없으면 빈 집합이다.
     *
     * <p>코드가 venue가 아는 지역이 아니면 venue가 400 오류를 던진다 — 값 집합을 소유한 쪽이 판정한다. 호출자는 코드 목록을 알 필요가 없다.
     *
     * @throws NullPointerException regionCode가 null이면 던진다 — "필터 없음"은 호출자가 이 메서드를 부르지 않는 것으로 표현한다.
     */
    Set<Long> findIdsByRegion(String regionCode);
}
