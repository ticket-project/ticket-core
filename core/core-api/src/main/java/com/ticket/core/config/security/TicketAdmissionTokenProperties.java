package com.ticket.core.config.security;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "security.admission")
public class TicketAdmissionTokenProperties {

    private String issuer = "ticket-queue";
    private String audience = "ticket-api";
    private String secretKey;
    private long expirationSeconds = 300L;

    public void setIssuer(final String issuer) {
        this.issuer = issuer;
    }

    public void setAudience(final String audience) {
        this.audience = audience;
    }

    public void setSecretKey(final String secretKey) {
        this.secretKey = secretKey;
    }

    public void setExpirationSeconds(final long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }
}
