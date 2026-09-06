package com.ticket.catalog.infrastructure.seat;

import com.ticket.catalog.domain.seat.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataSeatJpaRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByVenueId(Long venueId);
}
