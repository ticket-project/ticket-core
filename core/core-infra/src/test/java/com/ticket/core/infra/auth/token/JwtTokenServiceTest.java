package com.ticket.core.infra.auth.token;

import com.ticket.core.app.auth.token.AccessTokenReadResult;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class JwtTokenServiceTest {

    private static final String ISSUER = "ticket";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final String OTHER_SECRET_KEY = "abcdefghijabcdefghijabcdefghijab";
    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void 발급한_토큰에서_인증_주체를_읽는다() {
        JwtTokenService jwtTokenService = jwtTokenService();

        String token = jwtTokenService.createAccessToken(7L, "MEMBER");

        assertThat(jwtTokenService.read(token))
                .isInstanceOfSatisfying(AccessTokenReadResult.Authenticated.class, authenticated -> {
                    assertThat(authenticated.member().memberId()).isEqualTo(7L);
                    assertThat(authenticated.member().role()).isEqualTo("MEMBER");
                });
        assertThat(jwtTokenService.getAccessTokenExpirationSeconds()).isEqualTo(1800L);
    }

    @Test
    void 만료된_토큰은_만료로_구분해_돌려준다() {
        String token = jwtTokenService().createAccessToken(7L, "MEMBER");
        JwtTokenService laterService = new JwtTokenService(
                properties(),
                Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC)
        );

        assertThat(laterService.read(token)).isInstanceOf(AccessTokenReadResult.Expired.class);
    }

    @Test
    void 서명이_다른_토큰은_invalid로_돌려준다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("7")
                .claim("role", "MEMBER")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(1800)))
                .signWith(Keys.hmacShaKeyFor(OTHER_SECRET_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void 형식이_아닌_문자열은_invalid로_돌려준다() {
        assertThat(jwtTokenService().read("not-a-token")).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void exp가_없는_토큰은_invalid로_돌려준다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("7")
                .claim("role", "MEMBER")
                .issuedAt(Date.from(NOW))
                .signWith(secretKey())
                .compact();

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void role이_없는_토큰은_invalid로_돌려준다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("7")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(1800)))
                .signWith(secretKey())
                .compact();

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    @Test
    void subject가_없는_토큰은_invalid로_돌려준다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .claim("role", "MEMBER")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(1800)))
                .signWith(secretKey())
                .compact();

        assertThat(jwtTokenService().read(token)).isInstanceOf(AccessTokenReadResult.Invalid.class);
    }

    private JwtTokenService jwtTokenService() {
        return new JwtTokenService(properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(ISSUER);
        properties.setSecretKey(SECRET_KEY);
        properties.setAccessTokenExpirationSeconds(1800L);
        return properties;
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }
}
