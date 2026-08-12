package com.ticket.core.config.admission;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class AdmissionEnforcementObservabilityTest {

    @Test
    void default_development_state_is_visible_without_exposing_secret() {
        TicketAdmissionTokenProperties properties = new TicketAdmissionTokenProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new AdmissionEnforcementMetrics(properties).bindTo(meterRegistry);
        Health health = new AdmissionEnforcementHealthIndicator(properties).health();

        assertThat(meterRegistry.get("security.admission.enforcement.enabled").gauge().value()).isZero();
        assertThat(meterRegistry.get("security.admission.custom.secret.configured").gauge().value()).isZero();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("enforcementEnabled", false)
                .containsEntry("customSecretConfigured", false)
                .doesNotContainValue(TicketAdmissionTokenProperties.DEVELOPMENT_DEFAULT_SECRET);
    }

    @Test
    void enabled_state_and_custom_secret_are_reflected_immediately() {
        TicketAdmissionTokenProperties properties = new TicketAdmissionTokenProperties();
        properties.setEnforcementEnabled(true);
        properties.setSecretKey("fedcba9876543210fedcba9876543210");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        new AdmissionEnforcementMetrics(properties).bindTo(meterRegistry);

        assertThat(meterRegistry.get("security.admission.enforcement.enabled").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("security.admission.custom.secret.configured").gauge().value()).isEqualTo(1.0);
        assertThat(new AdmissionEnforcementHealthIndicator(properties).health().getDetails())
                .containsEntry("enforcementEnabled", true)
                .containsEntry("customSecretConfigured", true);
    }
}
