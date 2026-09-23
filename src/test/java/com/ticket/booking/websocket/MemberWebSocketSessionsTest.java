package com.ticket.booking.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

class MemberWebSocketSessionsTest {
    private final MemberWebSocketSessions sessions = new MemberWebSocketSessions();
    private final WebSocketHandler handler = sessions.decorate(mock(WebSocketHandler.class));

    @Test
    void withdrawal_closes_only_the_members_live_sessions() throws Exception {
        final WebSocketSession first = session("first");
        final WebSocketSession second = session("second");
        final WebSocketSession other = session("other");
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.afterConnectionEstablished(other);
        org.assertj.core.api.Assertions.assertThat(sessions.bind("first", 7L)).isTrue();
        org.assertj.core.api.Assertions.assertThat(sessions.bind("second", 7L)).isTrue();
        org.assertj.core.api.Assertions.assertThat(sessions.bind("other", 8L)).isTrue();

        sessions.close(7L);

        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(second).close(CloseStatus.POLICY_VIOLATION);
        verify(other, org.mockito.Mockito.never()).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void closed_transport_is_removed_from_tracking() throws Exception {
        final WebSocketSession session = session("closed");
        handler.afterConnectionEstablished(session);
        sessions.bind("closed", 7L);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        sessions.close(7L);

        verify(session, org.mockito.Mockito.never()).close(CloseStatus.POLICY_VIOLATION);
        org.assertj.core.api.Assertions.assertThat(sessions.bind("closed", 7L)).isFalse();
    }

    private static WebSocketSession session(final String id) {
        final WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
