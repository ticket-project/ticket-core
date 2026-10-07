package com.ticket.booking.admission;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.jspecify.annotations.Nullable;

import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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

    JwtAdmissionVerifier(final AdmissionTokenSettings settings, final Clock clock, final boolean enforcementEnabled) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(settings.secretKey().getBytes(StandardCharsets.UTF_8));
        this.enforcementEnabled = enforcementEnabled;
    }

    /**
     * 공개 진입점. 검증 실패는 admission 예외로 그대로 나간다 — 예외가 HTTP 상태와 E-code를 스스로 들고 있으므로 여기서 다시 번역하지 않는다. 대기열이 필요한지는 호출자가 이미
     * 판단했으므로 여기서 회차 정책을 조회하지 않는다.
     */
    @Override
    public void verify(final long performanceId, final long memberId, final @Nullable String admissionToken) {
        if (!enforcementEnabled) {
            return;
        }
        if (admissionToken == null || admissionToken.isBlank()) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8000, "admission token missing");
        }
        final Claims claims = parse(admissionToken);
        validateAudience(claims);
        validateScope(claims);
        validateTimestamps(claims);

        // 토큰이 가리키는 회원·회차가 이 요청과 같아야 한다. 둘 다 읽은 뒤에 비교한다 — 형식 오류가 불일치보다 먼저 드러난다.
        final long tokenMemberId = parseMemberId(claims);
        final long tokenPerformanceId = readLongClaim(claims, PERFORMANCE_ID_CLAIM);
        if (tokenMemberId != memberId) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token member mismatch");
        }
        if (tokenPerformanceId != performanceId) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token performance mismatch");
        }
    }

    private Claims parse(final String token) {
        try {
            return Jwts.parser()
                    .requireIssuer(settings.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8001, "admission token expired", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid", exception);
        }
    }

    private void validateAudience(final Claims claims) {
        if (claims.getAudience() == null || !claims.getAudience().contains(settings.audience())) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid audience");
        }
    }

    private void validateScope(final Claims claims) {
        if (!SCOPE.equals(claims.get(SCOPE_CLAIM, String.class))) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid scope");
        }
    }

    private void validateTimestamps(final Claims claims) {
        if (claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid timestamps");
        }
    }

    private long parseMemberId(final Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid subject", exception);
        }
    }

    private long readLongClaim(final Claims claims, final String claimName) {
        Object value = claims.get(claimName);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException exception) {
                throw new AdmissionTokenException(
                        AdmissionErrorCode.E8002, "admission token invalid " + claimName, exception);
            }
        }
        throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid " + claimName);
    }
}
