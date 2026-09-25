package com.ticket.security.exception;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.exception.TicketException;

/** 인증은 됐지만 자원에 대한 권한이 없다. */
public final class AuthorizationException extends TicketException {
    private static final String MESSAGE = "권한이 없습니다.";

    public AuthorizationException() {
        this(null);
    }

    public AuthorizationException(final @Nullable String detail) {
        super(SecurityErrorCode.E1001, MESSAGE, detail);
    }
}
