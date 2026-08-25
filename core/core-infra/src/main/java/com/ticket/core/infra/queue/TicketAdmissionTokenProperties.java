package com.ticket.core.infra.queue;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.admission")
public class TicketAdmissionTokenProperties {

    private boolean enforcementEnabled = false;
    private String issuer = "ticket-queue";
    private String audience = "ticket-api";
    private String secretKey = "0123456789abcdef0123456789abcdef";
    private long expirationSeconds = 300L;
}
