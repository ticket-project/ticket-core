package com.ticket.core.config.admission;

import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.PerformanceFinder;
import com.ticket.core.domain.queue.model.QueueMode;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.support.security.admission.AdmissionTokenException;
import com.ticket.support.security.admission.AdmissionTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdmissionTokenValidatorTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-05-24T12:00:00+09:00");

    private final PerformanceFinder performanceFinder = mock(PerformanceFinder.class);
    private final AdmissionTokenService admissionTokenService = mock(AdmissionTokenService.class);
    private final AdmissionTokenValidator validator = new AdmissionTokenValidator(
            performanceFinder,
            admissionTokenService,
            Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"))
    );

    @Test
    void direct_회차는_admission_token_없이_통과한다() {
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_OFF));

        validator.validate(10L, null);

        verifyNoInteractions(admissionTokenService);
    }

    @Test
    void queue_회차는_admission_token이_필수다() {
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));

        assertThatThrownBy(() -> validator.validate(10L, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.ADMISSION_TOKEN_REQUIRED);
    }

    @Test
    void queue_회차는_admission_token의_회차와_만료를_검증한다() {
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));

        validator.validate(10L, "admission-token");

        verify(admissionTokenService).verifyForPerformance("admission-token", 10L);
    }

    @Test
    void 만료된_admission_token은_거부한다() {
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));
        when(admissionTokenService.verifyForPerformance("expired-token", 10L))
                .thenThrow(new AdmissionTokenException("admission token expired"));

        assertThatThrownBy(() -> validator.validate(10L, "expired-token"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.ADMISSION_TOKEN_EXPIRED);
    }

    @Test
    void 잘못된_admission_token은_거부한다() {
        when(performanceFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));
        when(admissionTokenService.verifyForPerformance("invalid-token", 10L))
                .thenThrow(new AdmissionTokenException("admission token invalid"));

        assertThatThrownBy(() -> validator.validate(10L, "invalid-token"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.ADMISSION_TOKEN_INVALID);
    }

    private Performance performance(final QueueMode queueMode) {
        LocalDateTime now = NOW.toLocalDateTime();
        Performance performance = new Performance(
                null,
                1L,
                now.plusHours(1),
                now.plusHours(3),
                now.minusMinutes(10),
                now.plusHours(2),
                2,
                600
        );
        performance.updateQueuePolicy(queueMode, null, null, null, now.minusMinutes(5), null, null);
        return performance;
    }
}
