package com.ticket.core.config.admission;

import com.ticket.core.domain.performance.query.PerformanceBookingPolicyFinder;
import com.ticket.core.domain.performance.query.model.PerformanceBookingPolicyView;
import com.ticket.core.domain.queue.model.QueueMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 이 검증기의 남은 책임은 "언제 입장 검사를 하는가"다.
 * "토큰이 유효한가"는 {@link AdmissionTokenServiceTest}가 검증한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class AdmissionTokenValidatorTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-05-24T12:00:00+09:00");

    private final PerformanceBookingPolicyFinder performanceBookingPolicyFinder = mock(PerformanceBookingPolicyFinder.class);
    private final AdmissionTokenService admissionTokenService = mock(AdmissionTokenService.class);
    private final AdmissionTokenValidator validator = new AdmissionTokenValidator(
            performanceBookingPolicyFinder,
            admissionTokenService,
            Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul"))
    );

    @Test
    void admission_token_검증이_비활성화되면_회차와_토큰을_조회하지_않는다() {
        AdmissionTokenValidator disabledValidator = new AdmissionTokenValidator(
                performanceBookingPolicyFinder,
                admissionTokenService,
                Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Seoul")),
                false
        );

        disabledValidator.validate(10L, 10L, null);

        verifyNoInteractions(performanceBookingPolicyFinder, admissionTokenService);
    }

    @Test
    void direct_회차는_입장_검사를_위임하지_않는다() {
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_OFF));

        validator.validate(10L, 10L, null);

        verify(admissionTokenService, never()).ensureAdmitted(10L, 10L, null);
    }

    @Test
    void queue_회차는_입장_검사를_위임한다() {
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(performance(QueueMode.FORCE_ON));

        validator.validate(10L, 10L, "admission-token");

        verify(admissionTokenService).ensureAdmitted(10L, 10L, "admission-token");
    }

    @Test
    void auto_회차는_주입된_clock_기준으로_입장_검사를_위임한다() {
        when(performanceBookingPolicyFinder.findById(10L)).thenReturn(performance(QueueMode.AUTO));

        validator.validate(10L, 10L, "admission-token");

        verify(admissionTokenService).ensureAdmitted(10L, 10L, "admission-token");
    }

    private PerformanceBookingPolicyView performance(final QueueMode queueMode) {
        LocalDateTime now = NOW.toLocalDateTime();
        return new PerformanceBookingPolicyView(
                10L,
                now.minusMinutes(10),
                now.plusHours(2),
                2,
                600,
                queueMode,
                null,
                now.minusMinutes(5),
                null,
                null
        );
    }
}
