package com.ticket.booking.ticket.infrastructure;

import com.ticket.booking.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataTicketJpaRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketKey(String ticketKey);

    Optional<Ticket> findByOrderSeatId(Long orderSeatId);
}
