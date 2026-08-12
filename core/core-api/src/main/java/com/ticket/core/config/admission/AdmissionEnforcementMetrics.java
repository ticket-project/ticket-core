package com.ticket.core.config.admission;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionEnforcementMetrics implements MeterBinder {

    private final TicketAdmissionTokenProperties properties;

    @Override
    public void bindTo(final MeterRegistry registry) {
        Gauge.builder(
                        "security.admission.enforcement.enabled",
                        properties,
                        value -> value.isEnforcementEnabled() ? 1.0 : 0.0
                )
                .register(registry);
        Gauge.builder(
                        "security.admission.custom.secret.configured",
                        properties,
                        value -> value.hasCustomSecret() ? 1.0 : 0.0
                )
                .register(registry);
    }
}
