package com.ticket.core.config.admission;

public class AdmissionTokenException extends RuntimeException {

    public AdmissionTokenException(final String message) {
        super(message);
    }

    public AdmissionTokenException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
