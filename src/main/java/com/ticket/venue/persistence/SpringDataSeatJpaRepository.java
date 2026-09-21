package com.ticket.venue.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.venue.domain.Seat;

/**
 * 물리 좌석 조회다. {@code Seat.venueId}는 Venue가 다른 aggregate라 raw 컬럼이므로 join 없이 그대로 건다.
 *
 * <p>엔티티를 그대로 돌려주고, 공개 계약 타입으로의 변환은 {@link SeatRepositoryAdapter}가 한다.
 */
interface SpringDataSeatJpaRepository extends JpaRepository<Seat, Long> {
    List<Seat> findAllByVenueIdAndIdIn(Long venueId, Collection<Long> seatIds);

    List<Seat> findAllByVenueId(Long venueId);
}
