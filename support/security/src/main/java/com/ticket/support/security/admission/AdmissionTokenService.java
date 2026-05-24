package com.ticket.support.security.admission;

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

public class AdmissionTokenService {

    public static final String SCOPE = "ticket-admission";

    private static final String PERFORMANCE_ID_CLAIM = "performanceId";
    private static final String SCOPE_CLAIM = "scope";

    private final AdmissionTokenProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;

    public AdmissionTokenService(final AdmissionTokenProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public AdmissionTokenService(final AdmissionTokenProperties properties, final Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(final Long memberId, final Long performanceId) {
        validateId(memberId, "memberId");
        validateId(performanceId, "performanceId");

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(properties.expirationSeconds());

        return Jwts.builder()
                .issuer(properties.issuer())
                .audience()
                .add(properties.audience())
                .and()
                .subject(String.valueOf(memberId))
                .claim(PERFORMANCE_ID_CLAIM, performanceId)
                .claim(SCOPE_CLAIM, SCOPE)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    public AdmissionClaims verify(final String token) {
        Claims claims = parse(token);
        validateAudience(claims);
        validateScope(claims);

        return new AdmissionClaims(
                parseSubject(claims),
                readLongClaim(claims, PERFORMANCE_ID_CLAIM),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant(),
                claims.getId(),
                claims.get(SCOPE_CLAIM, String.class)
        );
    }

    public AdmissionClaims verifyFor(final String token, final Long memberId, final Long performanceId) {
        AdmissionClaims claims = verify(token);
        if (!claims.memberId().equals(memberId)) {
            throw new AdmissionTokenException("admission token member mismatch");
        }
        if (!claims.performanceId().equals(performanceId)) {
            throw new AdmissionTokenException("admission token performance mismatch");
        }
        return claims;
    }

    private Claims parse(final String token) {
        if (token == null || token.isBlank()) {
            throw new AdmissionTokenException("admission token invalid");
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
            throw new AdmissionTokenException("admission token expired", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AdmissionTokenException("admission token invalid", exception);
        }
    }

    private void validateAudience(final Claims claims) {
        if (!claims.getAudience().contains(properties.audience())) {
            throw new AdmissionTokenException("admission token invalid audience");
        }
    }

    private void validateScope(final Claims claims) {
        if (!SCOPE.equals(claims.get(SCOPE_CLAIM, String.class))) {
            throw new AdmissionTokenException("admission token invalid scope");
        }
    }

    private Long parseSubject(final Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new AdmissionTokenException("admission token invalid subject", exception);
        }
    }

    private Long readLongClaim(final Claims claims, final String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException exception) {
                throw new AdmissionTokenException("admission token invalid " + claimName, exception);
            }
        }
        throw new AdmissionTokenException("admission token invalid " + claimName);
    }

    private void validateId(final Long value, final String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
