package com.ticket.core.config.security;

import com.ticket.core.domain.member.model.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String ISSUER = "ticket";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");

    @Test
    void createAndParseAccessToken() {
        JwtTokenService jwtTokenService = jwtTokenService();

        String token = jwtTokenService.createAccessToken(new MemberPrincipal(7L, Role.MEMBER));

        MemberPrincipal principal = jwtTokenService.parse(token);
        assertThat(principal.getMemberId()).isEqualTo(7L);
        assertThat(principal.getRole()).isEqualTo(Role.MEMBER);
        assertThat(jwtTokenService.getAccessTokenExpirationSeconds()).isEqualTo(1800L);
    }

    @Test
    void parse는_exp_없는_access_token을_거부한다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("7")
                .claim("role", Role.MEMBER.name())
                .issuedAt(Date.from(NOW))
                .signWith(secretKey())
                .compact();

        assertThatThrownBy(() -> jwtTokenService().parse(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT required claim is missing");
    }

    @Test
    void parse는_role_없는_access_token을_거부한다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject("7")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(1800)))
                .signWith(secretKey())
                .compact();

        assertThatThrownBy(() -> jwtTokenService().parse(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT required claim is missing");
    }

    @Test
    void parse는_subject_없는_access_token을_거부한다() {
        String token = Jwts.builder()
                .issuer(ISSUER)
                .claim("role", Role.MEMBER.name())
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(1800)))
                .signWith(secretKey())
                .compact();

        assertThatThrownBy(() -> jwtTokenService().parse(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT required claim is missing");
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
