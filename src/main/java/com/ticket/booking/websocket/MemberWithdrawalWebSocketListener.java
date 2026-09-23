package com.ticket.booking.websocket;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ticket.member.api.MemberWithdrawn;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MemberWithdrawalWebSocketListener {
    private final MemberWebSocketSessions sessions;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(final MemberWithdrawn event) {
        sessions.close(event.memberId());
    }
}
