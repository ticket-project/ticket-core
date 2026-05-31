package com.ticket.core.config.security;

import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.internal-auth")
public class InternalAuthProperties {

    private String issuer = "ticket-gateway";
    private String audience = "ticket-core";
    private String secretKey;
    private long expirationSeconds = 60L;

    public void validate() {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("security.internal-auth.issuer must not be blank");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("security.internal-auth.audience must not be blank");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("security.internal-auth.secret-key must not be blank");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.internal-auth.secret-key must be at least 32 bytes for HS256");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("security.internal-auth.expiration-seconds must be positive");
        }
    }
}
