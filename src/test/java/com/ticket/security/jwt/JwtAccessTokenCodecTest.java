package com.ticket.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.ticket.security.token.AccessTokenReadResult;

@SuppressWarnings("NonAsciiCharacters")
class JwtAccessTokenCodecTest {
    private static final String ISSUER = "ticket";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final String SECRET_KEY_64 = SECRET_KEY + SECRET_KEY;
    private static final String OTHER_SECRET_KEY = "abcdefghijabcdefghijabcdefghijab";
    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final long IAT = NOW.getEpochSecond();

    /** jjwt 0.13 JwtAccessTokenCodec이 SECRET_KEY, NOW, memberId 7, MEMBER, 1800초로 발급한 토큰(HS256). */
    private static final String JJWT_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0aWNrZXQiLCJzdWIiOiI3Iiwicm9sZSI6Ik1FTUJFUiIsImlhdCI6MTc4MTgyNzIwMCwiZXhwIjoxNzgxODI5MDAwfQ.fyURPiVon86j_q8Br0-YoHNVUu-ofzoMXRpxSs1S86E";

    /** 같은 조건에서 64바이트 secret(SECRET_KEY_64)으로 jjwt가 발급한 토큰 — jjwt는 키 길이로 HS512를 골랐다. */
    private static final String JJWT_TOKEN_HS512 =
            "eyJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJ0aWNrZXQiLCJzdWIiOiI3Iiwicm9sZSI6Ik1FTUJFUiIsImlhdCI6MTc4MTgyNzIwMCwiZXhwIjoxNzgxODI5MDAwfQ.leD-mpdBjXQLhoqaEMVR3YmT_Hnfqo_86w1xBwbxrJd5XzAlFp7JFaUavT4619J4fNBXOQ5CXNWIpaRo_Yzu9Q";

    @Test
    void 발급한_토큰에서_인증_주체를_읽는다() {
        JwtAccessTokenCodec jwtTokenService = jwtTokenService();

        String token = jwtTokenService.createAccessToken(7L, "MEMBER");

        assertAuthenticated(jwtTokenService.read(token));
    }

    @Test
    void jjwt와_같은_header와_claim으로_발급하고_jjwt_토큰을_읽는다() throws ParseException {
        // claim 순서만 다르다(jjwt iat→exp, Nimbus exp→iat). header 문자열과 claim 값은 같다.
        assertSameEncoding(jwtTokenService().createAccessToken(7L, "MEMBER"), JJWT_TOKEN);
        assertSameEncoding(codec(SECRET_KEY_64, NOW).createAccessToken(7L, "MEMBER"), JJWT_TOKEN_HS512);

        assertAuthenticated(jwtTokenService().read(JJWT_TOKEN));
        assertAuthenticated(codec(SECRET_KEY_64, NOW).read(JJWT_TOKEN_HS512));
    }

    @Test
    void jjwt_토큰도_만료와_서명_불일치를_전과_같이_구분한다() {
        assertThat(codec(SECRET_KEY, NOW.plusSeconds(3600)).read(JJWT_TOKEN))
                .isInstanceOf(AccessTokenReadResult.Expired.class);
        assertThat(codec(OTHER_SECRET_KEY, NOW).read(JJWT_TOKEN)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void 만료된_토큰은_만료로_구분해_돌려준다() {
        String token = jwtTokenService().createAccessToken(7L, "MEMBER");

        assertThat(codec(SECRET_KEY, NOW.plusSeconds(3600)).read(token))
                .isInstanceOf(AccessTokenReadResult.Expired.class);
    }

    @Test
    void 허용_오차_없이_exp_순간까지만_유효하다() {
        assertAuthenticated(codec(SECRET_KEY, NOW.plusSeconds(1800)).read(JJWT_TOKEN));
        assertThat(codec(SECRET_KEY, NOW.plusSeconds(1800).plusMillis(1)).read(JJWT_TOKEN))
                .isInstanceOf(AccessTokenReadResult.Expired.class);
    }

    @Test
    void 만료는_issuer_불일치보다_먼저_판정한다() {
        String token = sign(SECRET_KEY, JWSAlgorithm.HS256, claims("other", "\"7\"", "\"MEMBER\"", ""));

        assertThat(codec(SECRET_KEY, NOW.plusSeconds(3600)).read(token))
                .isInstanceOf(AccessTokenReadResult.Expired.class);
        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void 서명이_다른_토큰은_invalid로_돌려준다() {
        String token = sign(OTHER_SECRET_KEY, JWSAlgorithm.HS256, claims(ISSUER, "\"7\"", "\"MEMBER\"", ""));

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void secret_길이가_허용하는_HS_알고리즘만_받는다() {
        String payload = claims(ISSUER, "\"7\"", "\"MEMBER\"", "");

        assertAuthenticated(codec(SECRET_KEY_64, NOW).read(sign(SECRET_KEY_64, JWSAlgorithm.HS256, payload)));
        assertAuthenticated(codec(SECRET_KEY_64, NOW).read(sign(SECRET_KEY_64, JWSAlgorithm.HS384, payload)));
        assertThat(jwtTokenService().read(hs512WithShortKey(payload)))
                .isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void alg_none_토큰은_invalid로_돌려준다() {
        String token = base64("{\"alg\":\"none\"}") + "." + base64(claims(ISSUER, "\"7\"", "\"MEMBER\"", "")) + ".";

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void nbf_이전이면_invalid로_돌려준다() {
        String token =
                sign(SECRET_KEY, JWSAlgorithm.HS256, claims(ISSUER, "\"7\"", "\"MEMBER\"", ",\"nbf\":" + (IAT + 10)));

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
        assertAuthenticated(codec(SECRET_KEY, NOW.plusSeconds(10)).read(token));
    }

    @Test
    void 숫자_subject는_invalid로_돌려준다() {
        String token = sign(SECRET_KEY, JWSAlgorithm.HS256, claims(ISSUER, "7", "\"MEMBER\"", ""));

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void 형식이_아닌_문자열은_invalid로_돌려준다() {
        assertThat(jwtTokenService().read("not-a-token")).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void exp가_없는_토큰은_invalid로_돌려준다() {
        String token = sign(
                SECRET_KEY,
                JWSAlgorithm.HS256,
                "{\"iss\":\"ticket\",\"sub\":\"7\",\"role\":\"MEMBER\",\"iat\":" + IAT + "}");

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void role이_없거나_문자열이_아니면_invalid로_돌려준다() {
        String withoutRole = sign(
                SECRET_KEY,
                JWSAlgorithm.HS256,
                "{\"iss\":\"ticket\",\"sub\":\"7\",\"iat\":" + IAT + ",\"exp\":" + (IAT + 1800) + "}");

        assertThat(jwtTokenService().read(withoutRole)).isInstanceOf(AccessTokenReadResult.Invalid.class);
        assertThat(jwtTokenService().read(sign(SECRET_KEY, JWSAlgorithm.HS256, claims(ISSUER, "\"7\"", "1", ""))))
                .isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void subject가_없는_토큰은_invalid로_돌려준다() {
        String token = sign(
                SECRET_KEY,
                JWSAlgorithm.HS256,
                "{\"iss\":\"ticket\",\"role\":\"MEMBER\",\"iat\":" + IAT + ",\"exp\":" + (IAT + 1800) + "}");

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void secret이_32바이트보다_짧으면_생성되지_않는다() {
        assertThatThrownBy(() -> codec(SECRET_KEY.substring(1), NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertSameEncoding(final String issued, final String jjwtToken) throws ParseException {
        String[] actual = issued.split("[.]");
        String[] expected = jjwtToken.split("[.]");
        assertThat(actual[0]).isEqualTo(expected[0]);
        assertThat(JSONObjectUtils.parse(new Base64URL(actual[1]).decodeToString()))
                .isEqualTo(JSONObjectUtils.parse(new Base64URL(expected[1]).decodeToString()));
    }

    private static void assertAuthenticated(final AccessTokenReadResult result) {
        assertThat(result).isInstanceOfSatisfying(AccessTokenReadResult.Authenticated.class, authenticated -> {
            assertThat(authenticated.member().memberId()).isEqualTo(7L);
            assertThat(authenticated.member().role()).isEqualTo("MEMBER");
        });
    }

    private JwtAccessTokenCodec jwtTokenService() {
        return codec(SECRET_KEY, NOW);
    }

    private static JwtAccessTokenCodec codec(final String secretKey, final Instant now) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(ISSUER);
        properties.setSecretKey(secretKey);
        properties.setAccessTokenExpirationSeconds(1800L);
        return new JwtAccessTokenCodec(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String claims(final String issuer, final String subject, final String role, final String extra) {
        return "{\"iss\":\"" + issuer + "\",\"sub\":" + subject + ",\"role\":" + role + ",\"iat\":" + IAT + ",\"exp\":"
                + (IAT + 1800) + extra + "}";
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

    /** Nimbus MACSigner는 짧은 키로 HS512 서명을 거부하므로 JCA로 직접 서명한다. */
    private static String hs512WithShortKey(final String payloadJson) {
        try {
            String signingInput = base64("{\"alg\":\"HS512\"}") + "." + base64(payloadJson);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return signingInput + "."
                    + Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String base64(final String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
