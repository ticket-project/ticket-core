package com.ticket.support.error;

import lombok.Getter;

@Getter
public class NotFoundException extends RuntimeException {

    private final ErrorDefinition errorType;
    private final Object data;

    public NotFoundException(final ErrorDefinition errorType) {
        this(errorType, null);
    }

    public NotFoundException(final ErrorDefinition errorType, final Object data) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = data;
    }

}
