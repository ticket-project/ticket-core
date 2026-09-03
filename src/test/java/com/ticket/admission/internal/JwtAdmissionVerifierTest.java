package com.ticket.admission.internal;

import com.ticket.admission.AdmissionVerification;
import com.ticket.admission.internal.exception.AdmissionErrorCode;
import com.ticket.admission.internal.exception.AdmissionTokenException;
import com.ticket.admission.internal.exception.AdmissionTokenExpiredException;
import com.ticket.admission.internal.exception.AdmissionTokenRequiredException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

class JwtAdmissionVerifierTest {

    private static final String ISSUER = "ticket-queue";
    private static final String AUDIENCE = "ticket-api";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void verify는_admission_token_claim을_파싱한다() {
        AdmissionClaims claims = jwtAdmissionVerifier().verify(admissionToken(true, true, true, "10"));

        assertThat(claims.subject()).isEqualTo("10");
        assertThat(claims.memberId()).isEqualTo(10L);
        assertThat(claims.performanceId()).isEqualTo(20L);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(claims.scope()).isEqualTo(JwtAdmissionVerifier.SCOPE);
    }

    @Test
    void verifyFor_accepts_the_queue_issuer_contract_for_the_same_member_and_performance() {
        AdmissionClaims claims = jwtAdmissionVerifier()
                .verifyFor(admissionToken(true, true, true, "10"), 10L, 20L);

        assertThat(claims.memberId()).isEqualTo(10L);
        assertThat(claims.performanceId()).isEqualTo(20L);
    }

    @Test
    void verifyFor_rejects_a_token_bound_to_another_member() {
        assertThatThrownBy(() ->
                jwtAdmissionVerifier().verifyFor(admissionToken(true, true, true, "10"), 11L, 20L)
        )
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token member mismatch"));
    }

    @Test
    void verifyFor_rejects_a_token_bound_to_another_performance() {
        assertThatThrownBy(() ->
                jwtAdmissionVerifier().verifyFor(admissionToken(true, true, true, "10"), 10L, 21L)
        )
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token performance mismatch"));
    }

    @Test
    void verify는_audience_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(admissionToken(false, true, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid audience"));
    }

    @Test
    void verify는_iat_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(admissionToken(true, false, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid timestamps"));
    }

    @Test
    void verify는_exp_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(admissionToken(true, true, false, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid timestamps"));
    }

    @Test
    void verify는_숫자가_아닌_subject를_invalid로_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(admissionToken(true, true, true, "member-10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid subject"));
    }

    @Test
    void verify는_만료된_admission_token을_만료_예외로_거부한다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("10")
                .claim("aud", List.of(AUDIENCE))
                .claim("performanceId", 20L)
                .claim("scope", JwtAdmissionVerifier.SCOPE)
                .issuedAt(Date.from(NOW.minusSeconds(600)))
                .expiration(Date.from(NOW.minusSeconds(300)))
                .signWith(secretKey())
                .compact();

        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(token))
                .isInstanceOf(AdmissionTokenExpiredException.class)
                .hasMessage("대기열 입장 토큰이 만료되었습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token expired"));
    }

    @Test
    void verify는_유효한_token에_대해_공개_불변_result를_돌려준다() {
        AdmissionVerification result =
                jwtAdmissionVerifier().verify(20L, 10L, admissionToken(true, true, true, "10"));

        assertThat(result).isEqualTo(new AdmissionVerification(20L, 10L));
    }

    @Test
    void verify는_token이_없으면_required로_거부한다() {
        assertAdmissionError(null, AdmissionTokenRequiredException.class, AdmissionErrorCode.E8000);
        assertAdmissionError("   ", AdmissionTokenRequiredException.class, AdmissionErrorCode.E8000);
    }

    @Test
    void verify는_만료된_token을_expired로_거부한다() {
        String expired = Jwts.builder()
                .issuer(ISSUER)
                .subject("10")
                .claim("aud", List.of(AUDIENCE))
                .claim("performanceId", 20L)
                .claim("scope", JwtAdmissionVerifier.SCOPE)
                .issuedAt(Date.from(NOW.minusSeconds(600)))
                .expiration(Date.from(NOW.minusSeconds(300)))
                .signWith(secretKey())
                .compact();

        assertAdmissionError(expired, AdmissionTokenExpiredException.class, AdmissionErrorCode.E8001);
    }

    @Test
    void verify는_계약을_어긴_token을_invalid로_거부한다() {
        assertAdmissionError(
                admissionToken(false, true, true, "10"),
                AdmissionTokenException.class,
                AdmissionErrorCode.E8002);
        assertAdmissionError("not-a-jwt", AdmissionTokenException.class, AdmissionErrorCode.E8002);
    }

    @Test
    void verify는_다른_회원의_token을_invalid로_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier()
                .verify(20L, 11L, admissionToken(true, true, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getErrorCode())
                        .isEqualTo(AdmissionErrorCode.E8002));
    }

    @Test
    void enforcement가_꺼져있으면_token_없이도_통과시킨다() {
        JwtAdmissionVerifier disabled = new JwtAdmissionVerifier(
                new AdmissionTokenSettings(ISSUER, AUDIENCE, SECRET_KEY, 300),
                Clock.fixed(NOW, ZoneOffset.UTC),
                false
        );

        assertThatCode(() -> disabled.verify(20L, 10L, null)).doesNotThrowAnyException();
    }

    /**
     * 예외 타입과 E-code를 함께 본다 — 타입만 보면 만료(E8001)와 무효(E8002)가 상속 관계라
     * 구분되지 않고, code만 보면 어느 예외가 던져졌는지 놓친다.
     */
    private void assertAdmissionError(
            final String token,
            final Class<? extends AdmissionTokenException> expectedType,
            final AdmissionErrorCode expectedCode
    ) {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, token))
                .isInstanceOf(expectedType)
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getErrorCode())
                        .isEqualTo(expectedCode));
    }

    private JwtAdmissionVerifier jwtAdmissionVerifier() {
        return new JwtAdmissionVerifier(
                new AdmissionTokenSettings(ISSUER, AUDIENCE, SECRET_KEY, 300),
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
                .claim("scope", JwtAdmissionVerifier.SCOPE)
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
