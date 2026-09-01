package com.ticket.core.support.exception;

import lombok.Getter;

import java.util.Objects;

@Getter
public class CoreException extends RuntimeException {

    private final ErrorType errorType;
    private final Object data;

    public CoreException(final ErrorType errorType) {
        this(errorType, null);
    }

    public CoreException(final ErrorType errorType, final Object data) {
        super(errorType.getMessage());
        this.errorType = Objects.requireNonNull(errorType);
        this.data = data;
    }
}
