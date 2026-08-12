package com.ticket.core.config.admission;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("admissionEnforcement")
@RequiredArgsConstructor
public class AdmissionEnforcementHealthIndicator implements HealthIndicator {

    private final TicketAdmissionTokenProperties properties;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("enforcementEnabled", properties.isEnforcementEnabled())
                .withDetail("customSecretConfigured", properties.hasCustomSecret())
                .build();
    }
}
