package com.ticket.support.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenVerifierTest {

    private static final String ISSUER = "ticket";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void issueAndVerifyAccessToken() {
        JwtAccessTokenIssuer issuer = accessTokenIssuer(NOW, 1800L);
        JwtTokenVerifier verifier = tokenVerifier(NOW);

        String token = issuer.issue(7L, "MEMBER");

        JwtMemberClaims claims = verifier.verify(token);
        assertThat(claims.memberId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo("MEMBER");
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(1800L));
    }

    @Test
    void rejectExpiredAccessToken() {
        JwtAccessTokenIssuer issuer = accessTokenIssuer(NOW, 1800L);
        JwtTokenVerifier verifier = tokenVerifier(NOW.plusSeconds(1801L));
        String token = issuer.issue(7L, "MEMBER");

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectTamperedAccessToken() {
        JwtAccessTokenIssuer issuer = accessTokenIssuer(NOW, 1800L);
        JwtTokenVerifier verifier = tokenVerifier(NOW);
        String token = issuer.issue(7L, "MEMBER");
        char replacement = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';
        String tamperedToken = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> verifier.verify(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    private JwtAccessTokenIssuer accessTokenIssuer(final Instant now, final long expirationSeconds) {
        return new JwtAccessTokenIssuer(jwtProperties(expirationSeconds), Clock.fixed(now, ZoneOffset.UTC));
    }

    private JwtTokenVerifier tokenVerifier(final Instant now) {
        return new JwtTokenVerifier(jwtProperties(1800L), Clock.fixed(now, ZoneOffset.UTC));
    }

    private JwtProperties jwtProperties(final long expirationSeconds) {
        return new JwtProperties(ISSUER, SECRET_KEY, expirationSeconds);
    }
}
