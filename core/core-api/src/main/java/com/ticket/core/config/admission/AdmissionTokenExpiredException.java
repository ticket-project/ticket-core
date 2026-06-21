package com.ticket.core.config.admission;

public class AdmissionTokenExpiredException extends AdmissionTokenException {

    public AdmissionTokenExpiredException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
