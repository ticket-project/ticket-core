package com.ticket.venue.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.venue.domain.Seat;

interface SpringDataSeatJpaRepository extends JpaRepository<Seat, Long> {
    List<Seat> findAllByVenueId(Long venueId);
}
