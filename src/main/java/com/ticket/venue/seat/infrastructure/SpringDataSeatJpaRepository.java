package com.ticket.venue.seat.infrastructure;

import com.ticket.venue.seat.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataSeatJpaRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByVenueId(Long venueId);
}
