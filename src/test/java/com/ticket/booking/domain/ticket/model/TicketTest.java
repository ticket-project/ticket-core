package com.ticket.booking.domain.ticket.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class TicketTest {

    @Test
    void 티켓을_발급하면_issued_상태로_초기화된다() {
        //given
        LocalDateTime issuedAt = LocalDateTime.of(2026, 3, 15, 12, 0);

        //when
        Ticket ticket = createTicket(issuedAt);

        //then
        assertThat(ticket.getTicketKey()).isEqualTo("ticket-key");
        assertThat(ticket.getOrderSeatId()).isEqualTo(1L);
        assertThat(ticket.getOwnerMemberId()).isEqualTo(10L);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
        assertThat(ticket.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(ticket.isTerminal()).isFalse();
    }

    @Test
    void issued_상태는_사용_처리할_수_있다() {
        //given
        LocalDateTime usedAt = LocalDateTime.of(2026, 3, 15, 18, 0);
        Ticket ticket = createTicket(LocalDateTime.of(2026, 3, 15, 12, 0));

        //when
        ticket.use(usedAt);

        //then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(ticket.getUsedAt()).isEqualTo(usedAt);
        assertThat(ticket.isTerminal()).isTrue();
    }

    @Test
    void issued_상태는_취소할_수_있다() {
        //given
        LocalDateTime canceledAt = LocalDateTime.of(2026, 3, 15, 13, 0);
        Ticket ticket = createTicket(LocalDateTime.of(2026, 3, 15, 12, 0));

        //when
        ticket.cancel(canceledAt);

        //then
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CANCELED);
        assertThat(ticket.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(ticket.isTerminal()).isTrue();
    }

    @Test
    void 종결_상태의_티켓은_다시_전이할_수_없다() {
        //given
        Ticket ticket = createTicket(LocalDateTime.of(2026, 3, 15, 12, 0));
        ticket.use(LocalDateTime.of(2026, 3, 15, 18, 0));

        //when
        //then
        assertThatThrownBy(() -> ticket.use(LocalDateTime.of(2026, 3, 15, 19, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=USED");
        assertThatThrownBy(() -> ticket.cancel(LocalDateTime.of(2026, 3, 15, 19, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentStatus=USED");
    }

    private Ticket createTicket(final LocalDateTime issuedAt) {
        return Ticket.issue("ticket-key", 1L, 10L, issuedAt);
    }
}
