package com.ticket.support.security.admission;

import java.nio.charset.StandardCharsets;

public record AdmissionTokenProperties(
        String issuer,
        String audience,
        String secretKey,
        long expirationSeconds
) {

    public AdmissionTokenProperties {
        if (isBlank(issuer)) {
            throw new IllegalArgumentException("admission token issuer must not be blank");
        }
        if (isBlank(audience)) {
            throw new IllegalArgumentException("admission token audience must not be blank");
        }
        if (isBlank(secretKey)) {
            throw new IllegalArgumentException("admission token secret key must not be blank");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("admission token secret key must be at least 32 bytes for HS256");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("admission token expiration seconds must be positive");
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
