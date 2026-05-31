package com.ticket.support.security.internalauth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public class InternalAuthTokenService {

    private static final String ROLE_CLAIM = "role";

    private final InternalAuthTokenProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;

    public InternalAuthTokenService(final InternalAuthTokenProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InternalAuthTokenService(final InternalAuthTokenProperties properties, final Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(final Long memberId, final String role) {
        validateMemberId(memberId);
        validateRole(role);

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(properties.expirationSeconds());

        return Jwts.builder()
                .issuer(properties.issuer())
                .audience()
                .add(properties.audience())
                .and()
                .subject(String.valueOf(memberId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    public InternalAuthClaims verify(final String token) {
        Claims claims = parse(token);
        validateAudience(claims);

        return new InternalAuthClaims(
                parseMemberId(claims.getSubject()),
                claims.get(ROLE_CLAIM, String.class),
                properties.audience(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant(),
                claims.getId()
        );
    }

    private Claims parse(final String token) {
        if (token == null || token.isBlank()) {
            throw new InternalAuthTokenException("internal auth token invalid");
        }

        try {
            return Jwts.parser()
                    .requireIssuer(properties.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new InternalAuthTokenException("internal auth token expired", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InternalAuthTokenException("internal auth token invalid", exception);
        }
    }

    private void validateAudience(final Claims claims) {
        if (!claims.getAudience().contains(properties.audience())) {
            throw new InternalAuthTokenException("internal auth token invalid audience");
        }
    }

    private Long parseMemberId(final String subject) {
        try {
            Long memberId = Long.parseLong(subject);
            validateMemberId(memberId);
            return memberId;
        } catch (NumberFormatException exception) {
            throw new InternalAuthTokenException("internal auth token invalid subject", exception);
        }
    }

    private void validateMemberId(final Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
    }

    private void validateRole(final String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
