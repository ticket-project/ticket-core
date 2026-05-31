package com.ticket.core.config.admission;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.PerformanceFinder;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.support.security.admission.AdmissionTokenException;
import com.ticket.support.security.admission.AdmissionTokenService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AdmissionTokenValidator {

    public static final String HEADER = "X-Admission-Token";

    private final PerformanceFinder performanceFinder;
    private final AdmissionTokenService admissionTokenService;
    private final Clock clock;

    public AdmissionTokenValidator(
            final PerformanceFinder performanceFinder,
            final AdmissionTokenService admissionTokenService
    ) {
        this(performanceFinder, admissionTokenService, Clock.systemDefaultZone());
    }

    AdmissionTokenValidator(
            final PerformanceFinder performanceFinder,
            final AdmissionTokenService admissionTokenService,
            final Clock clock
    ) {
        this.performanceFinder = Objects.requireNonNull(performanceFinder, "performanceFinder must not be null");
        this.admissionTokenService =
                Objects.requireNonNull(admissionTokenService, "admissionTokenService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void validate(final Long performanceId, final String admissionToken) {
        Performance performance = performanceFinder.findById(performanceId);
        if (!performance.requiresQueueAt(LocalDateTime.now(clock))) {
            return;
        }

        if (admissionToken == null || admissionToken.isBlank()) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED);
        }

        try {
            admissionTokenService.verifyForPerformance(admissionToken, performanceId);
        } catch (AdmissionTokenException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("expired")) {
                throw new CoreException(ErrorType.ADMISSION_TOKEN_EXPIRED);
            }
            throw new CoreException(ErrorType.ADMISSION_TOKEN_INVALID);
        }
    }
}
