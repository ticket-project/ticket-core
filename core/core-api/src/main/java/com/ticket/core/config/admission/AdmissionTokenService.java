package com.ticket.core.config.admission;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;

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

    AdmissionTokenService(final AdmissionTokenProperties properties, final Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
    }

    public AdmissionClaims verify(final String token) {
        Claims claims = parse(token);
        validateAudience(claims);
        validateScope(claims);
        validateTimestamps(claims);

        return new AdmissionClaims(
                claims.getSubject(),
                parseMemberId(claims),
                readLongClaim(claims, PERFORMANCE_ID_CLAIM),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant(),
                claims.getId(),
                claims.get(SCOPE_CLAIM, String.class)
        );
    }

    public AdmissionClaims verifyFor(final String token, final Long memberId, final Long performanceId) {
        AdmissionClaims claims = verify(token);
        if (!Objects.equals(claims.memberId(), memberId)) {
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
            throw new AdmissionTokenExpiredException("admission token expired", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AdmissionTokenException("admission token invalid", exception);
        }
    }

    private void validateAudience(final Claims claims) {
        if (claims.getAudience() == null || !claims.getAudience().contains(properties.audience())) {
            throw new AdmissionTokenException("admission token invalid audience");
        }
    }

    private void validateScope(final Claims claims) {
        if (!SCOPE.equals(claims.get(SCOPE_CLAIM, String.class))) {
            throw new AdmissionTokenException("admission token invalid scope");
        }
    }

    private void validateTimestamps(final Claims claims) {
        if (claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new AdmissionTokenException("admission token invalid timestamps");
        }
    }

    private Long parseMemberId(final Claims claims) {
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
}
