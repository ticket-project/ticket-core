package com.ticket.core.config.admission;

import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdmissionTokenValidator {

    private final PerformanceBookingPolicyFinder performanceBookingPolicyFinder;
    private final AdmissionTokenService admissionTokenService;
    private final Clock clock;
    private final boolean enforcementEnabled;

    @Autowired
    public AdmissionTokenValidator(
            final PerformanceBookingPolicyFinder performanceBookingPolicyFinder,
            final AdmissionTokenService admissionTokenService,
            final Clock clock,
            final TicketAdmissionTokenProperties properties
    ) {
        this(
                performanceBookingPolicyFinder,
                admissionTokenService,
                clock,
                properties.isEnforcementEnabled()
        );
    }

    AdmissionTokenValidator(
            final PerformanceBookingPolicyFinder performanceBookingPolicyFinder,
            final AdmissionTokenService admissionTokenService,
            final Clock clock
    ) {
        this(performanceBookingPolicyFinder, admissionTokenService, clock, true);
    }

    AdmissionTokenValidator(
            final PerformanceBookingPolicyFinder performanceBookingPolicyFinder,
            final AdmissionTokenService admissionTokenService,
            final Clock clock,
            final boolean enforcementEnabled
    ) {
        this.performanceBookingPolicyFinder = Objects.requireNonNull(
                performanceBookingPolicyFinder, "performanceBookingPolicyFinder must not be null");
        this.admissionTokenService =
                Objects.requireNonNull(admissionTokenService, "admissionTokenService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.enforcementEnabled = enforcementEnabled;
    }

    public void validate(final Long performanceId, final Long memberId, final String admissionToken) {
        if (!enforcementEnabled) {
            return;
        }

        PerformanceBookingPolicyView performance = performanceBookingPolicyFinder.findById(performanceId);
        if (!performance.requiresQueueAt(LocalDateTime.now(clock))) {
            return;
        }

        admissionTokenService.ensureAdmitted(performanceId, memberId, admissionToken);
    }
}
