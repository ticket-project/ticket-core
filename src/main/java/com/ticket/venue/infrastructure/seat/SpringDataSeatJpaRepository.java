package com.ticket.venue.infrastructure.seat;

import com.ticket.venue.domain.seat.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataSeatJpaRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByVenueId(Long venueId);
}
