package com.ticket.show.usecase;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.ticket.venue.api.VenueSnapshot;

/**
 * show 목록·상세 조회가 venue 표시값(이름·지역)을 배치로 채울 때 쓰는 작은 값 객체다. venueId가 venue module에 실제로 없는 dangling id여도 예외 없이 null을 돌려준다 —
 * cross-module FK가 없어(ADR 0003 §4) DB가 막아주지 않는 상태이고, 목록 한 건 때문에 응답 전체를 실패시키지 않는다(과거 {@code leftJoin} 결과와 동일).
 */
public record VenueDisplays(Map<Long, VenueSnapshot> byId) {
    public @Nullable VenueSnapshot get(final long venueId) {
        return byId.get(venueId);
    }

    public @Nullable String nameOf(final long venueId) {
        final VenueSnapshot summary = get(venueId);
        return summary == null ? null : summary.name();
    }

    /** 응답에 싣는 지역 코드({@code "SEOUL"})다. */
    public @Nullable String regionCodeOf(final long venueId) {
        final VenueSnapshot summary = get(venueId);
        return summary == null || summary.region() == null
                ? null
                : summary.region().code();
    }
}
