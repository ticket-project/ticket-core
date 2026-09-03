package com.ticket.identity.internal.exception;

import org.springframework.http.HttpStatus;

/**
 * 인증은 됐지만 그 자원에 대한 권한이 없다.
 */
public class AuthorizationException extends IdentityException {

    private static final String MESSAGE = "권한이 없습니다.";

    public AuthorizationException() {
        this(null);
    }

    public AuthorizationException(final Object data) {
        super(HttpStatus.FORBIDDEN, IdentityErrorCode.E1001, MESSAGE, data);
    }
}
