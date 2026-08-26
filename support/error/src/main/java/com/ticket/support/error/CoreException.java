package com.ticket.support.error;

import lombok.Getter;

@Getter
public class CoreException extends RuntimeException {

    private final ErrorDefinition errorType;
    private final Object data;

    public CoreException(final ErrorDefinition errorType) {
        this(errorType, null);
    }

    public CoreException(final ErrorDefinition errorType, final Object data) {
        super(errorType.getMessage());
        this.errorType = errorType;
        this.data = data;
    }

}
