package com.ticket.support.error;

import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

    private final ErrorDefinition errorType;
    private final Object data;

    public AuthException(final ErrorDefinition errorType) {
        this(errorType, null);
    }

    public AuthException(final ErrorDefinition errorType, final Object data) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = data;
    }

}
