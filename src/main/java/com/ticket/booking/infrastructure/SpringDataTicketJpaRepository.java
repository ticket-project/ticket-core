package com.ticket.booking.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticket.booking.domain.ticket.Ticket;

interface SpringDataTicketJpaRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketKey(String ticketKey);

    Optional<Ticket> findByOrderSeatId(Long orderSeatId);
}
