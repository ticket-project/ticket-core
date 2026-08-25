package com.ticket.support.error;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

    private final ErrorType errorType;
    private final Object data;

    public AuthException(final ErrorType errorType) {
        this(errorType, null);
    }

    public AuthException(final ErrorType errorType, Object data) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = data;
    }

}
