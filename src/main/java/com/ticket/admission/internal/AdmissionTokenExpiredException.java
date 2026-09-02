package com.ticket.admission.internal;

public class AdmissionTokenExpiredException extends AdmissionTokenException {

    public AdmissionTokenExpiredException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
