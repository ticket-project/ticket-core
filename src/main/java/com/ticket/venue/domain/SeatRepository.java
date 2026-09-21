package com.ticket.venue.domain;

import java.util.Collection;
import java.util.List;

/**
 * Seat aggregate의 복원을 담당하는 도메인 Repository다.
 *
 * <p>{@link VenueRepository}와 나눠 둔다 — Seat은 Venue와 다른 Aggregate라 {@code Seat.venueId}가 연관관계가 아니라
 * raw 컬럼이다.
 */
public interface SeatRepository {
    List<Seat> findAllByVenueIdAndIdIn(Long venueId, Collection<Long> seatIds);

    List<Seat> findAllByVenueId(Long venueId);
}
