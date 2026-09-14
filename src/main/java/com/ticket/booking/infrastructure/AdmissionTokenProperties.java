package com.ticket.booking.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.admission")
public class AdmissionTokenProperties {
    private boolean enforcementEnabled = false;
    private String issuer = "ticket-queue";
    private String audience = "ticket-api";
    private String secretKey = "0123456789abcdef0123456789abcdef";
    private long expirationSeconds = 300L;
}
