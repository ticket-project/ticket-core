package com.ticket.show.application;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;

/**
 * 지역 검색 조건을 venueId 집합으로 해석한다. show는 venue module의 Region을 자기 DB query에 쓰지 않고, 이렇게 얻은 venueId에 대해서만
 * 조건을 건다 — 조회 port는 자기 DB query 구성에만 집중하고, 다른 module의 데이터를 준비하는 일은 application이 한다.
 *
 * <p><b>"지역 없음"과 "지역은 있으나 그 지역에 공연장이 없음"은 다른 결과다.</b> 그래서 {@code null}(지역 조건 자체가 없음)과 빈 집합(조건은 있는데
 * 해당 공연장이 없으니 결과 0건)을 구분해 돌려준다. 이 둘을 뭉개면 "제주에 공연장이 하나도 없다"가 "전체 목록"으로 조용히 바뀐다.
 *
 * <p>네 use case가 같은 규칙을 쓰므로 한곳에 둔다 — 한쪽만 고치고 다른 쪽을 잊으면 그 회귀는 테스트 없이는 보이지 않는다.
 */
public final class RegionVenueIds {
    private RegionVenueIds() {}

    /**
     * @return 지역 조건이 없으면 {@code null}. 있으면 그 지역 공연장의 id 집합이며, 비어 있을 수 있다
     */
    public static @Nullable Set<Long> resolve(
            final VenueLookupApi venueLookup, final @Nullable Region region) {
        return region == null ? null : venueLookup.findIdsByRegion(region);
    }
}
