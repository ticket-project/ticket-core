package com.ticket.booking.ticket.domain;

import com.ticket.booking.ticket.domain.Ticket;

import java.util.Optional;

/**
 * Ticket(입장 권리) aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>조회 실패는 {@link Optional}로 돌려주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가
 * 고른다. 예외를 던지는 {@code getXxx}/{@code requireXxx} 편의 메서드는 두지 않는다.
 */
public interface TicketRepository {

    Ticket save(Ticket ticket);

    Optional<Ticket> findById(Long id);

    Optional<Ticket> findByTicketKey(String ticketKey);

    Optional<Ticket> findByOrderSeatId(Long orderSeatId);
}
