package com.ticket.venue.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.ticket.venue.domain.Seat;
import com.ticket.venue.domain.SeatRepository;

import lombok.RequiredArgsConstructor;

/** {@link SeatRepository}의 JPA 구현이다. */
@Repository
@RequiredArgsConstructor
public class SeatRepositoryAdapter implements SeatRepository {
    private final SpringDataSeatJpaRepository jpaRepository;

    @Override
    public List<Seat> findAllByVenueIdAndIdIn(final Long venueId, final Collection<Long> seatIds) {
        return jpaRepository.findAllByVenueIdAndIdIn(venueId, seatIds);
    }

    @Override
    public List<Seat> findAllByVenueId(final Long venueId) {
        return jpaRepository.findAllByVenueId(venueId);
    }
}
