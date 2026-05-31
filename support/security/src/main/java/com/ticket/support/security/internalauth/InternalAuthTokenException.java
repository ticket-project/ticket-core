package com.ticket.support.security.internalauth;

public class InternalAuthTokenException extends RuntimeException {

    public InternalAuthTokenException(final String message) {
        super(message);
    }

    public InternalAuthTokenException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
