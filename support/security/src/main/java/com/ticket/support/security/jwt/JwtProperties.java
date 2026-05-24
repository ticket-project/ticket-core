package com.ticket.support.security.jwt;

import java.nio.charset.StandardCharsets;

public record JwtProperties(
        String issuer,
        String secretKey,
        long accessTokenExpirationSeconds
) {

    public JwtProperties {
        if (isBlank(issuer)) {
            throw new IllegalArgumentException("security.jwt.issuer must not be blank");
        }
        if (isBlank(secretKey)) {
            throw new IllegalArgumentException("security.jwt.secret-key must not be blank");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret-key must be at least 32 bytes for HS256");
        }
        if (accessTokenExpirationSeconds <= 0) {
            throw new IllegalArgumentException("security.jwt.access-token-expiration-seconds must be positive");
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
