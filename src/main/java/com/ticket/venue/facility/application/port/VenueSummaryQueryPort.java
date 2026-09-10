package com.ticket.venue.facility.application.port;

import com.ticket.venue.Region;
import com.ticket.venue.VenueSummary;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link com.ticket.venue.VenueLookup}이 쓰는 venue 표시값 조회 포트다.
 */
public interface VenueSummaryQueryPort {

    Optional<VenueSummary> findSummary(long venueId);

    /**
     * 빈 {@code venueIds}는 빈 map을 반환한다.
     */
    Map<Long, VenueSummary> findSummaries(Set<Long> venueIds);

    Set<Long> findIdsByRegion(Region region);
}
