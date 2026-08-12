package com.ticket.core.config.admission;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.admission")
public class TicketAdmissionTokenProperties {

    public static final String DEVELOPMENT_DEFAULT_SECRET = "0123456789abcdef0123456789abcdef";

    private boolean enforcementEnabled = false;
    private String issuer = "ticket-queue";
    private String audience = "ticket-api";
    private String secretKey = DEVELOPMENT_DEFAULT_SECRET;
    private long expirationSeconds = 300L;

    public boolean hasCustomSecret() {
        return secretKey != null
                && !secretKey.isBlank()
                && !DEVELOPMENT_DEFAULT_SECRET.equals(secretKey);
    }
}
