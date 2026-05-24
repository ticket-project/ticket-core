package com.ticket.support.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

public class JwtAccessTokenIssuer {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;

    public JwtAccessTokenIssuer(final JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtAccessTokenIssuer(final JwtProperties properties, final Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(final Long memberId, final String role) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(properties.accessTokenExpirationSeconds());

        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(memberId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }
}
