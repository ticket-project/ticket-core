package com.ticket.booking.websocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import lombok.extern.slf4j.Slf4j;

/** Tracks this process's live transports so a committed withdrawal can close them. */
@Component
@Slf4j
public class MemberWebSocketSessions implements WebSocketHandlerDecoratorFactory {
    private final Map<String, WebSocketSession> sessions = new HashMap<>();
    private final Map<Long, Set<String>> memberSessions = new HashMap<>();
    private final Map<String, Long> sessionMembers = new HashMap<>();

    @Override
    public WebSocketHandler decorate(final WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(final WebSocketSession session) throws Exception {
                synchronized (MemberWebSocketSessions.this) {
                    sessions.put(session.getId(), session);
                }
                try {
                    super.afterConnectionEstablished(session);
                } catch (final Exception exception) {
                    remove(session.getId());
                    throw exception;
                }
            }

            @Override
            public void afterConnectionClosed(final WebSocketSession session, final CloseStatus closeStatus)
                    throws Exception {
                try {
                    super.afterConnectionClosed(session, closeStatus);
                } finally {
                    remove(session.getId());
                }
            }
        };
    }

    public synchronized boolean bind(final String sessionId, final long memberId) {
        if (!sessions.containsKey(sessionId)) {
            return false;
        }
        final Long previous = sessionMembers.put(sessionId, memberId);
        if (previous != null && previous != memberId) {
            removeMembership(previous, sessionId);
        }
        memberSessions.computeIfAbsent(memberId, ignored -> new HashSet<>()).add(sessionId);
        return true;
    }

    public void close(final long memberId) {
        final java.util.List<WebSocketSession> toClose;
        synchronized (this) {
            final Set<String> ids = memberSessions.remove(memberId);
            if (ids == null) {
                return;
            }
            toClose = new ArrayList<>(ids.size());
            for (final String id : ids) {
                sessionMembers.remove(id);
                final WebSocketSession session = sessions.get(id);
                if (session != null) {
                    toClose.add(session);
                }
            }
        }
        for (final WebSocketSession session : toClose) {
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.POLICY_VIOLATION);
                }
            } catch (final Exception exception) {
                log.warn("탈퇴 회원의 WebSocket 종료에 실패했습니다. sessionId={}", session.getId(), exception);
            }
        }
    }

    private synchronized void remove(final String sessionId) {
        sessions.remove(sessionId);
        final Long memberId = sessionMembers.remove(sessionId);
        if (memberId != null) {
            removeMembership(memberId, sessionId);
        }
    }

    private void removeMembership(final long memberId, final String sessionId) {
        final Set<String> ids = memberSessions.get(memberId);
        if (ids != null) {
            ids.remove(sessionId);
            if (ids.isEmpty()) {
                memberSessions.remove(memberId);
            }
        }
    }
}
