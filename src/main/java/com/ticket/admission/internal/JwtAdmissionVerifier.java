package com.ticket.admission.internal;

import com.ticket.admission.AdmissionVerification;
import com.ticket.admission.AdmissionVerifier;
import com.ticket.admission.internal.exception.AdmissionTokenException;
import com.ticket.admission.internal.exception.AdmissionTokenExpiredException;
import com.ticket.admission.internal.exception.AdmissionTokenRequiredException;
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

public class JwtAdmissionVerifier implements AdmissionVerifier {

    public static final String SCOPE = "ticket-admission";

    private static final String PERFORMANCE_ID_CLAIM = "performanceId";
    private static final String SCOPE_CLAIM = "scope";

    private final AdmissionTokenSettings settings;
    private final Clock clock;
    private final SecretKey secretKey;
    private final boolean enforcementEnabled;

    public JwtAdmissionVerifier(final AdmissionTokenSettings settings, final boolean enforcementEnabled) {
        this(settings, Clock.systemUTC(), enforcementEnabled);
    }

    JwtAdmissionVerifier(final AdmissionTokenSettings settings, final Clock clock) {
        this(settings, clock, true);
    }

    JwtAdmissionVerifier(
            final AdmissionTokenSettings settings,
            final Clock clock,
            final boolean enforcementEnabled
    ) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(settings.secretKey().getBytes(StandardCharsets.UTF_8));
        this.enforcementEnabled = enforcementEnabled;
    }

    /**
     * 공개 진입점. 검증 실패는 이 module이 소유한 예외로 그대로 나간다 — 예외가 HTTP 상태와 E-code를
     * 스스로 들고 있으므로 여기서 다시 번역하지 않는다.
     * 대기열이 필요한지는 호출자가 이미 판단했으므로 여기서 회차 정책을 조회하지 않는다.
     */
    @Override
    public AdmissionVerification verify(final long performanceId, final long memberId, final String admissionToken) {
        if (!enforcementEnabled) {
            return new AdmissionVerification(performanceId, memberId);
        }
        if (admissionToken == null || admissionToken.isBlank()) {
            throw new AdmissionTokenRequiredException();
        }
        verifyFor(admissionToken, memberId, performanceId);
        return new AdmissionVerification(performanceId, memberId);
    }

    AdmissionClaims verify(final String token) {
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

    AdmissionClaims verifyFor(final String token, final long memberId, final long performanceId) {
        AdmissionClaims claims = verify(token);
        if (!Objects.equals(claims.memberId(), memberId)) {
            throw new AdmissionTokenException("admission token member mismatch");
        }
        if (!Objects.equals(claims.performanceId(), performanceId)) {
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
                    .requireIssuer(settings.issuer())
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
        if (claims.getAudience() == null || !claims.getAudience().contains(settings.audience())) {
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
