package com.ticket.core.app.auth.token;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public final class AuthRefreshToken {

    private final String value;

    private AuthRefreshToken(final String value) {
        this.value = value;
    }

    public static AuthRefreshToken from(final String value) {
        final String normalized = normalize(value);
        validate(normalized);
        return new AuthRefreshToken(normalized);
    }

    private static String normalize(final String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static void validate(final String value) {
        if (!value.isBlank()) {
            return;
        }
        throw new CoreException(ErrorType.AUTHENTICATION_ERROR);
    }

    public String value() {
        return value;
    }
}
