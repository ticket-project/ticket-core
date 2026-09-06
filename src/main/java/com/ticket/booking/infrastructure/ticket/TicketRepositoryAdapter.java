package com.ticket.booking.infrastructure.ticket;

import com.ticket.booking.domain.ticket.model.Ticket;
import com.ticket.booking.domain.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link TicketRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class TicketRepositoryAdapter implements TicketRepository {

    private final SpringDataTicketJpaRepository jpaRepository;

    @Override
    public Ticket save(final Ticket ticket) {
        return jpaRepository.save(ticket);
    }

    @Override
    public Optional<Ticket> findById(final Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Ticket> findByTicketKey(final String ticketKey) {
        return jpaRepository.findByTicketKey(ticketKey);
    }

    @Override
    public Optional<Ticket> findByOrderSeatId(final Long orderSeatId) {
        return jpaRepository.findByOrderSeatId(orderSeatId);
    }
}
