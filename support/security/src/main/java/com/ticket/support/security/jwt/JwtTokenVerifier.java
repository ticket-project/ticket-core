package com.ticket.support.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;

public class JwtTokenVerifier {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;

    public JwtTokenVerifier(final JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtTokenVerifier(final JwtProperties properties, final Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
    }

    public JwtMemberClaims verify(final String token) {
        Claims claims = Jwts.parser()
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new JwtMemberClaims(
                Long.parseLong(claims.getSubject()),
                claims.get(ROLE_CLAIM, String.class),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        );
    }
}
