package com.ticket.core.config.admission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class AdmissionTokenServiceTest {

    private static final String ISSUER = "ticket-queue";
    private static final String AUDIENCE = "ticket-api";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void verify는_admission_token_claim을_파싱한다() {
        AdmissionClaims claims = admissionTokenService().verify(admissionToken(true, true, true, "10"));

        assertThat(claims.subject()).isEqualTo("10");
        assertThat(claims.memberId()).isEqualTo(10L);
        assertThat(claims.performanceId()).isEqualTo(20L);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(claims.scope()).isEqualTo(AdmissionTokenService.SCOPE);
    }

    @Test
    void verifyFor_accepts_the_queue_issuer_contract_for_the_same_member_and_performance() {
        AdmissionClaims claims = admissionTokenService()
                .verifyFor(admissionToken(true, true, true, "10"), 10L, 20L);

        assertThat(claims.memberId()).isEqualTo(10L);
        assertThat(claims.performanceId()).isEqualTo(20L);
    }

    @Test
    void verifyFor_rejects_a_token_bound_to_another_member() {
        assertThatThrownBy(() ->
                admissionTokenService().verifyFor(admissionToken(true, true, true, "10"), 11L, 20L)
        )
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("admission token member mismatch");
    }

    @Test
    void verifyFor_rejects_a_token_bound_to_another_performance() {
        assertThatThrownBy(() ->
                admissionTokenService().verifyFor(admissionToken(true, true, true, "10"), 10L, 21L)
        )
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("admission token performance mismatch");
    }

    @Test
    void verify는_audience_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> admissionTokenService().verify(admissionToken(false, true, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("admission token invalid audience");
    }

    @Test
    void verify는_iat_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> admissionTokenService().verify(admissionToken(true, false, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("admission token invalid timestamps");
    }

    @Test
    void verify는_exp_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> admissionTokenService().verify(admissionToken(true, true, false, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("admission token invalid timestamps");
    }

    @Test
    void verify는_숫자가_아닌_subject를_invalid로_거부한다() {
        assertThatThrownBy(() -> admissionTokenService().verify(admissionToken(true, true, true, "member-10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("admission token invalid subject");
    }

    @Test
    void verify는_만료된_admission_token을_만료_예외로_거부한다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("10")
                .claim("aud", List.of(AUDIENCE))
                .claim("performanceId", 20L)
                .claim("scope", AdmissionTokenService.SCOPE)
                .issuedAt(Date.from(NOW.minusSeconds(600)))
                .expiration(Date.from(NOW.minusSeconds(300)))
                .signWith(secretKey())
                .compact();

        assertThatThrownBy(() -> admissionTokenService().verify(token))
                .isInstanceOf(AdmissionTokenExpiredException.class)
                .hasMessage("admission token expired");
    }

    private AdmissionTokenService admissionTokenService() {
        return new AdmissionTokenService(
                new AdmissionTokenProperties(ISSUER, AUDIENCE, SECRET_KEY, 300),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private String admissionToken(
            final boolean includeAudience,
            final boolean includeIssuedAt,
            final boolean includeExpiration,
            final String subject
    ) {
        JwtBuilder builder = Jwts.builder()
                .issuer(ISSUER)
                .subject(subject)
                .claim("performanceId", 20L)
                .claim("scope", AdmissionTokenService.SCOPE)
                .id("admission-token-id");
        if (includeAudience) {
            builder.claim("aud", List.of(AUDIENCE));
        }
        if (includeIssuedAt) {
            builder.issuedAt(Date.from(NOW));
        }
        if (includeExpiration) {
            builder.expiration(Date.from(NOW.plusSeconds(300)));
        }
        return builder.signWith(secretKey()).compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }
}
