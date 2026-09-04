package com.ticket.ticketing.internal.infrastructure.ticket;

import com.ticket.ticketing.internal.domain.ticket.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataTicketJpaRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketKey(String ticketKey);

    Optional<Ticket> findByOrderSeatId(Long orderSeatId);
}
