package com.ticket.core.infra.queue;

public class AdmissionTokenExpiredException extends AdmissionTokenException {

    public AdmissionTokenExpiredException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
