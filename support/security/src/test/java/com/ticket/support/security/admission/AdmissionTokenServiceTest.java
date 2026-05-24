package com.ticket.support.security.admission;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdmissionTokenServiceTest {

    private static final String ISSUER = "ticket-queue";
    private static final String AUDIENCE = "ticket-api";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void issueAndVerifyAdmissionToken() {
        AdmissionTokenService service = admissionTokenService(NOW, 300L);

        String token = service.issue(1L, 10L);

        AdmissionClaims claims = service.verify(token);
        assertThat(claims.memberId()).isEqualTo(1L);
        assertThat(claims.performanceId()).isEqualTo(10L);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(300L));
        assertThat(claims.scope()).isEqualTo("ticket-admission");
    }

    @Test
    void rejectExpiredAdmissionToken() {
        AdmissionTokenService issuer = admissionTokenService(NOW, 300L);
        AdmissionTokenService verifier = admissionTokenService(NOW.plusSeconds(301L), 300L);
        String token = issuer.issue(1L, 10L);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectMemberMismatch() {
        AdmissionTokenService service = admissionTokenService(NOW, 300L);
        String token = service.issue(1L, 10L);

        assertThatThrownBy(() -> service.verifyFor(token, 2L, 10L))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessageContaining("member");
    }

    @Test
    void rejectPerformanceMismatch() {
        AdmissionTokenService service = admissionTokenService(NOW, 300L);
        String token = service.issue(1L, 10L);

        assertThatThrownBy(() -> service.verifyFor(token, 1L, 11L))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessageContaining("performance");
    }

    @Test
    void rejectTamperedAdmissionToken() {
        AdmissionTokenService service = admissionTokenService(NOW, 300L);
        String token = service.issue(1L, 10L);
        String[] parts = token.split("\\.");
        char replacement = parts[1].charAt(0) == 'a' ? 'b' : 'a';
        String tamperedToken = parts[0] + "." + replacement + parts[1].substring(1) + "." + parts[2];

        assertThatThrownBy(() -> service.verify(tamperedToken))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessageContaining("invalid");
    }

    private AdmissionTokenService admissionTokenService(final Instant now, final long expirationSeconds) {
        AdmissionTokenProperties properties = new AdmissionTokenProperties(
                ISSUER,
                AUDIENCE,
                SECRET_KEY,
                expirationSeconds
        );
        return new AdmissionTokenService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
