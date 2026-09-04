package com.ticket.catalog.internal.infrastructure.seat;

import com.ticket.catalog.internal.domain.seat.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataSeatJpaRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByVenueId(Long venueId);
}
