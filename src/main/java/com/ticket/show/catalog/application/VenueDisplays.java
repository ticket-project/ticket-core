package com.ticket.show.catalog.application;

import com.ticket.venue.Region;
import com.ticket.venue.VenueLookup;
import com.ticket.venue.VenueSummary;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * show 목록·상세 조회가 venue 표시값(이름·지역)을 배치로 채울 때 쓰는 작은 값 객체다. venueId가
 * null이거나(venue 없는 show) venue module에 실제로 없는 dangling id여도 예외 없이 null을
 * 돌려준다 — 둘 다 "venue 없는 show"와 같은 값으로 통일한다(과거 {@code leftJoin} 결과와 동일).
 */
public record VenueDisplays(Map<Long, VenueSummary> byId) {

    public static VenueDisplays load(final VenueLookup venueLookup, final Collection<Long> venueIds) {
        final Set<Long> ids = venueIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        return new VenueDisplays(venueLookup.getSummaries(ids));
    }

    public VenueSummary get(final Long venueId) {
        return venueId == null ? null : byId.get(venueId);
    }

    public String nameOf(final Long venueId) {
        final VenueSummary summary = get(venueId);
        return summary == null ? null : summary.name();
    }

    public Region regionOf(final Long venueId) {
        final VenueSummary summary = get(venueId);
        return summary == null ? null : summary.region();
    }
}
