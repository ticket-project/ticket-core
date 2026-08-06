package com.ticket.core.config.admission;

import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AdmissionTokenValidator {

    public static final String HEADER = "X-Admission-Token";

    private final PerformanceBookingPolicyFinder performanceBookingPolicyFinder;
    private final AdmissionTokenService admissionTokenService;
    private final Clock clock;
    private final boolean enforcementEnabled;

    @Autowired
    public AdmissionTokenValidator(
            final PerformanceBookingPolicyFinder performanceBookingPolicyFinder,
            final AdmissionTokenService admissionTokenService,
            final TicketAdmissionTokenProperties properties
    ) {
        this(
                performanceBookingPolicyFinder,
                admissionTokenService,
                Clock.systemDefaultZone(),
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

        if (admissionToken == null || admissionToken.isBlank()) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED);
        }

        try {
            admissionTokenService.verifyFor(admissionToken, memberId, performanceId);
        } catch (AdmissionTokenExpiredException exception) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_EXPIRED);
        } catch (AdmissionTokenException exception) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_INVALID);
        }
    }
}
