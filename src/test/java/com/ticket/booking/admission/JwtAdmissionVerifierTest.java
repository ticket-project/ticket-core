package com.ticket.booking.admission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;
import com.ticket.booking.exception.AdmissionTokenExpiredException;
import com.ticket.booking.exception.AdmissionTokenRequiredException;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAdmissionVerifierTest {
    private static final String ISSUER = "ticket-queue";
    private static final String AUDIENCE = "ticket-api";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void verify는_다른_회원에게_묶인_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 11L, admissionToken(true, true, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token member mismatch"));
    }

    @Test
    void verify는_다른_회차에_묶인_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(21L, 10L, admissionToken(true, true, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token performance mismatch"));
    }

    @Test
    void verify는_audience_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, admissionToken(false, true, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid audience"));
    }

    @Test
    void verify는_iat_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, admissionToken(true, false, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid timestamps"));
    }

    @Test
    void verify는_exp_없는_admission_token을_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, admissionToken(true, true, false, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .hasMessage("대기열 입장 토큰이 올바르지 않습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid timestamps"));
    }

    @Test
    void verify는_숫자가_아닌_subject를_invalid로_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, admissionToken(true, true, true, "member-10")))
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

        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, token))
                .isInstanceOf(AdmissionTokenExpiredException.class)
                .hasMessage("대기열 입장 토큰이 만료되었습니다.")
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token expired"));
    }

    /** 통과하면 아무것도 돌려주지 않는다 — 호출자가 전부 반환값을 버리고 있어 검증 계약을 void로 단순화했다. */
    @Test
    void verify는_유효한_token이면_예외_없이_통과한다() {
        assertThatNoException()
                .isThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, admissionToken(true, true, true, "10")));
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
                admissionToken(false, true, true, "10"), AdmissionTokenException.class, AdmissionErrorCode.E8002);
        assertAdmissionError("not-a-jwt", AdmissionTokenException.class, AdmissionErrorCode.E8002);
    }

    @Test
    void verify는_다른_회원의_token을_invalid로_거부한다() {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 11L, admissionToken(true, true, true, "10")))
                .isInstanceOf(AdmissionTokenException.class)
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getErrorCode())
                        .isEqualTo(AdmissionErrorCode.E8002));
    }

    @Test
    void enforcement가_꺼져있으면_token_없이도_통과시킨다() {
        JwtAdmissionVerifier disabled = new JwtAdmissionVerifier(
                new AdmissionTokenSettings(ISSUER, AUDIENCE, SECRET_KEY), Clock.fixed(NOW, ZoneOffset.UTC), false);

        assertThatCode(() -> disabled.verify(20L, 10L, null)).doesNotThrowAnyException();
    }

    /** 예외 타입과 E-code를 함께 본다 — 타입만 보면 만료(E8001)와 무효(E8002)가 상속 관계라 구분되지 않고, code만 보면 어느 예외가 던져졌는지 놓친다. */
    private void assertAdmissionError(
            final String token,
            final Class<? extends AdmissionTokenException> expectedType,
            final AdmissionErrorCode expectedCode) {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, token))
                .isInstanceOf(expectedType)
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getErrorCode())
                        .isEqualTo(expectedCode));
    }

    private JwtAdmissionVerifier jwtAdmissionVerifier() {
        return new JwtAdmissionVerifier(
                new AdmissionTokenSettings(ISSUER, AUDIENCE, SECRET_KEY), Clock.fixed(NOW, ZoneOffset.UTC), true);
    }

    private String admissionToken(
            final boolean includeAudience,
            final boolean includeIssuedAt,
            final boolean includeExpiration,
            final String subject) {
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
