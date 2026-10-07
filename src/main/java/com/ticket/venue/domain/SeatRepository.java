package com.ticket.venue.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.data.repository.Repository;

/**
 * Seat aggregate의 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>{@link VenueRepository}와 나눠 둔다 — Seat은 Venue와 다른 Aggregate라 {@code Seat.venueId}가 연관관계가 아니라 raw 컬럼이고, 그래서 join 없이
 * 그대로 건다. 엔티티를 돌려주고 공개 계약 변환은 use case가 한다.
 */
public interface SeatRepository extends Repository<Seat, Long> {
    List<Seat> findAllByVenueIdAndIdIn(Long venueId, Collection<Long> seatIds);

    List<Seat> findAllByVenueId(Long venueId);
}
