package com.ticket.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.PerformanceFinder;
import com.ticket.core.domain.queue.model.QueueLevel;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.support.security.admission.AdmissionTokenException;
import com.ticket.support.security.admission.AdmissionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AdmissionTokenValidatorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-05-24T11:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final AdmissionTokenService admissionTokenService = mock(AdmissionTokenService.class);
    private final PerformanceFinder performanceFinder = mock(PerformanceFinder.class);
    private final AdmissionTokenValidator validator = new AdmissionTokenValidator(
            admissionTokenService,
            performanceFinder,
            CLOCK
    );

    @Test
    void missing_header_is_allowed_when_performance_does_not_require_queue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_OFF));

        validator.verify(request, 100L, 10L);

        verify(admissionTokenService, never()).verifyFor("", 100L, 10L);
    }

    @Test
    void missing_header_is_rejected_when_performance_requires_queue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));

        assertThatThrownBy(() -> validator.verify(request, 100L, 10L))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ADMISSION_TOKEN_REQUIRED));
    }

    @Test
    void invalid_token_is_rejected_when_performance_requires_queue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));
        when(request.getHeader(AdmissionTokenValidator.HEADER_NAME)).thenReturn("bad-token");
        when(admissionTokenService.verifyFor("bad-token", 100L, 10L))
                .thenThrow(new AdmissionTokenException("admission token invalid"));

        assertThatThrownBy(() -> validator.verify(request, 100L, 10L))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ADMISSION_TOKEN_INVALID));
    }

    @Test
    void expired_token_is_rejected_with_expired_error_type() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));
        when(request.getHeader(AdmissionTokenValidator.HEADER_NAME)).thenReturn("expired-token");
        when(admissionTokenService.verifyFor("expired-token", 100L, 10L))
                .thenThrow(new AdmissionTokenException("admission token expired"));

        assertThatThrownBy(() -> validator.verify(request, 100L, 10L))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ADMISSION_TOKEN_EXPIRED));
    }

    @Test
    void mismatched_token_is_rejected_with_mismatch_error_type() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));
        when(request.getHeader(AdmissionTokenValidator.HEADER_NAME)).thenReturn("mismatch-token");
        when(admissionTokenService.verifyFor("mismatch-token", 100L, 10L))
                .thenThrow(new AdmissionTokenException("admission token member mismatch"));

        assertThatThrownBy(() -> validator.verify(request, 100L, 10L))
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType())
                        .isEqualTo(ErrorType.ADMISSION_TOKEN_MISMATCH));
    }

    @Test
    void valid_token_is_accepted_when_performance_requires_queue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));
        when(request.getHeader(AdmissionTokenValidator.HEADER_NAME)).thenReturn("admission-token");

        validator.verify(request, 100L, 10L);

        verify(admissionTokenService).verifyFor("admission-token", 100L, 10L);
    }

    private Performance performance(final QueueMode queueMode) {
        Performance performance = new Performance(
                null,
                1L,
                LocalDateTime.of(2026, 5, 25, 20, 0),
                LocalDateTime.of(2026, 5, 25, 22, 0),
                LocalDateTime.of(2026, 5, 24, 20, 0),
                LocalDateTime.of(2026, 5, 24, 21, 0),
                4,
                300
        );
        performance.updateQueuePolicy(queueMode, QueueLevel.LEVEL_1, 300, 300, null, null, null);
        return performance;
    }
}