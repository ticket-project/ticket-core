package com.ticket.core.config;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.PerformanceFinder;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.support.security.admission.AdmissionTokenException;
import com.ticket.support.security.admission.AdmissionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdmissionTokenValidator {

    public static final String HEADER_NAME = "X-Admission-Token";

    private final AdmissionTokenService admissionTokenService;
    private final PerformanceFinder performanceFinder;
    private final Clock clock;

    public void verify(final HttpServletRequest request, final Long memberId, final Long performanceId) {
        final Performance performance = performanceFinder.findById(performanceId);
        if (!performance.requiresQueueAt(LocalDateTime.now(clock))) {
            return;
        }

        final String admissionToken = request.getHeader(HEADER_NAME);
        if (admissionToken == null || admissionToken.isBlank()) {
            throw new CoreException(ErrorType.ADMISSION_TOKEN_REQUIRED);
        }

        try {
            admissionTokenService.verifyFor(admissionToken, memberId, performanceId);
        } catch (final AdmissionTokenException exception) {
            throw new CoreException(resolveErrorType(exception));
        }
    }

    private ErrorType resolveErrorType(final AdmissionTokenException exception) {
        final String message = exception.getMessage();
        if (message != null && message.contains("expired")) {
            return ErrorType.ADMISSION_TOKEN_EXPIRED;
        }
        if (message != null && message.contains("mismatch")) {
            return ErrorType.ADMISSION_TOKEN_MISMATCH;
        }
        return ErrorType.ADMISSION_TOKEN_INVALID;
    }
}