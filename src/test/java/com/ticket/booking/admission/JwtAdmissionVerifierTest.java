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

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;

class JwtAdmissionVerifierTest {
    private static final String ISSUER = "ticket-queue";
    private static final String AUDIENCE = "ticket-api";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final String OTHER_SECRET_KEY = "abcdefghijabcdefghijabcdefghijab";
    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final long IAT = NOW.getEpochSecond();

    /**
     * ticket-queue SignedAdmissionTokenIssuer와 같은 방식으로 jjwt 0.13이 SECRET_KEY로 발급한 토큰. sub 10, performanceId 20, aud
     * [ticket-api], iat NOW, exp NOW+300초.
     */
    private static final String JJWT_ADMISSION_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0aWNrZXQtcXVldWUiLCJhdWQiOlsidGlja2V0LWFwaSJdLCJzdWIiOiIxMCIsInBlcmZvcm1hbmNlSWQiOjIwLCJxdWV1ZUlkIjoicXVldWUtMSIsInNjb3BlIjoidGlja2V0LWFkbWlzc2lvbiIsImlhdCI6MTc4MTgyNzIwMCwiZXhwIjoxNzgxODI3NTAwLCJqdGkiOiJhZG1pc3Npb24tdG9rZW4taWQifQ.bvlJm3-Hfc1E7QS_UYBkwqBaa0QF8QCQf-qJsj6UaJI";

    @Test
    void jjwt로_발급된_admission_token을_통과시킨다() {
        assertThatNoException().isThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, JJWT_ADMISSION_TOKEN));
    }

    @Test
    void jjwt로_발급된_token도_만료와_서명_불일치를_전과_같은_코드로_거부한다() {
        JwtAdmissionVerifier later = new JwtAdmissionVerifier(
                new AdmissionTokenProperties(true, ISSUER, AUDIENCE, SECRET_KEY),
                Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC));
        JwtAdmissionVerifier otherKey = new JwtAdmissionVerifier(
                new AdmissionTokenProperties(true, ISSUER, AUDIENCE, OTHER_SECRET_KEY),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> later.verify(20L, 10L, JJWT_ADMISSION_TOKEN))
                .isInstanceOf(AdmissionTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", AdmissionErrorCode.E8001)
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token expired"));
        assertThatThrownBy(() -> otherKey.verify(20L, 10L, JJWT_ADMISSION_TOKEN))
                .isInstanceOf(AdmissionTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", AdmissionErrorCode.E8002)
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getReason())
                        .isEqualTo("admission token invalid"));
    }

    @Test
    void 만료는_issuer_불일치보다_먼저이고_서명_불일치는_만료보다_먼저_판정한다() {
        JWTClaimsSet expiredOtherIssuer =
                new JWTClaimsSet.Builder(expiredClaims()).issuer("other").build();

        assertAdmissionError(sign(SECRET_KEY, JWSAlgorithm.HS256, expiredOtherIssuer), AdmissionErrorCode.E8001);
        assertAdmissionError(sign(OTHER_SECRET_KEY, JWSAlgorithm.HS256, expiredClaims()), AdmissionErrorCode.E8002);
    }

    @Test
    void verify는_secret_길이가_허용하지_않는_알고리즘과_alg_none을_거부한다() {
        String hs512 = sign(SECRET_KEY + SECRET_KEY, JWSAlgorithm.HS512, validClaims());
        String none = new PlainJWT(validClaims()).serialize();

        assertAdmissionError(hs512, AdmissionErrorCode.E8002);
        assertAdmissionError(none, AdmissionErrorCode.E8002);
    }

    @Test
    void verify는_문자열이_아닌_scope와_숫자_subject를_invalid로_거부한다() {
        String numericScope = sign(
                SECRET_KEY,
                JWSAlgorithm.HS256,
                new JWTClaimsSet.Builder(validClaims()).claim("scope", 1).build());
        String numericSubject = sign(
                SECRET_KEY,
                JWSAlgorithm.HS256,
                "{\"iss\":\"ticket-queue\",\"aud\":\"ticket-api\",\"sub\":10,\"performanceId\":20,"
                        + "\"scope\":\"ticket-admission\",\"iat\":" + IAT + ",\"exp\":" + (IAT + 300) + "}");

        assertAdmissionError(numericScope, AdmissionErrorCode.E8002);
        assertAdmissionError(numericSubject, AdmissionErrorCode.E8002);
    }

    @Test
    void secret이_32바이트보다_짧으면_생성되지_않는다() {
        assertThatThrownBy(() -> new JwtAdmissionVerifier(
                        new AdmissionTokenProperties(true, ISSUER, AUDIENCE, SECRET_KEY.substring(1)),
                        Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalArgumentException.class);
    }

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
        String token = sign(SECRET_KEY, JWSAlgorithm.HS256, expiredClaims());

        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, token))
                .isInstanceOf(AdmissionTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", AdmissionErrorCode.E8001)
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
        assertAdmissionError(null, AdmissionErrorCode.E8000);
        assertAdmissionError("   ", AdmissionErrorCode.E8000);
    }

    @Test
    void verify는_만료된_token을_expired로_거부한다() {
        assertAdmissionError(sign(SECRET_KEY, JWSAlgorithm.HS256, expiredClaims()), AdmissionErrorCode.E8001);
    }

    @Test
    void verify는_계약을_어긴_token을_invalid로_거부한다() {
        assertAdmissionError(admissionToken(false, true, true, "10"), AdmissionErrorCode.E8002);
        assertAdmissionError("not-a-jwt", AdmissionErrorCode.E8002);
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
                new AdmissionTokenProperties(false, ISSUER, AUDIENCE, SECRET_KEY), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatCode(() -> disabled.verify(20L, 10L, null)).doesNotThrowAnyException();
    }

    private void assertAdmissionError(final String token, final AdmissionErrorCode expectedCode) {
        assertThatThrownBy(() -> jwtAdmissionVerifier().verify(20L, 10L, token))
                .isInstanceOf(AdmissionTokenException.class)
                .satisfies(exception -> assertThat(((AdmissionTokenException) exception).getErrorCode())
                        .isEqualTo(expectedCode));
    }

    private JwtAdmissionVerifier jwtAdmissionVerifier() {
        return new JwtAdmissionVerifier(
                new AdmissionTokenProperties(true, ISSUER, AUDIENCE, SECRET_KEY), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String admissionToken(
            final boolean includeAudience,
            final boolean includeIssuedAt,
            final boolean includeExpiration,
            final String subject) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .claim("performanceId", 20L)
                .claim("scope", JwtAdmissionVerifier.SCOPE)
                .jwtID("admission-token-id");
        if (includeAudience) {
            builder.audience(AUDIENCE);
        }
        if (includeIssuedAt) {
            builder.issueTime(Date.from(NOW));
        }
        if (includeExpiration) {
            builder.expirationTime(Date.from(NOW.plusSeconds(300)));
        }
        return sign(SECRET_KEY, JWSAlgorithm.HS256, builder.build());
    }

    private static JWTClaimsSet validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("10")
                .claim("performanceId", 20L)
                .claim("scope", JwtAdmissionVerifier.SCOPE)
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .build();
    }

    private static JWTClaimsSet expiredClaims() {
        return new JWTClaimsSet.Builder(validClaims())
                .issueTime(Date.from(NOW.minusSeconds(600)))
                .expirationTime(Date.from(NOW.minusSeconds(300)))
                .build();
    }

    private static String sign(final String secretKey, final JWSAlgorithm algorithm, final JWTClaimsSet claims) {
        return sign(secretKey, algorithm, claims.toString());
    }

    private static String sign(final String secretKey, final JWSAlgorithm algorithm, final String payloadJson) {
        try {
            JWSObject jws = new JWSObject(new JWSHeader(algorithm), new Payload(payloadJson));
            jws.sign(new MACSigner(secretKey.getBytes(StandardCharsets.UTF_8)));
            return jws.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
