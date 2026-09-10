package com.ticket.venue.seat.infrastructure;

import com.ticket.venue.seat.domain.Seat;
import com.ticket.venue.seat.domain.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link SeatRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class SeatRepositoryAdapter implements SeatRepository {

    private final SpringDataSeatJpaRepository jpaRepository;

    @Override
    public List<Seat> findAllByVenueId(final Long venueId) {
        return jpaRepository.findAllByVenueId(venueId);
    }
}
