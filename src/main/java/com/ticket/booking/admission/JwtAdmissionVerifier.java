package com.ticket.booking.admission;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ticket.booking.exception.AdmissionErrorCode;
import com.ticket.booking.exception.AdmissionTokenException;

@Component
@EnableConfigurationProperties(AdmissionTokenProperties.class)
public class JwtAdmissionVerifier implements AdmissionVerifier {
    public static final String SCOPE = "ticket-admission";
    private static final String PERFORMANCE_ID_CLAIM = "performanceId";
    private static final String SCOPE_CLAIM = "scope";
    private final AdmissionTokenProperties properties;
    private final Clock clock;
    private final Set<JWSAlgorithm> acceptedAlgorithms;
    private final MACVerifier verifier;

    public JwtAdmissionVerifier(final AdmissionTokenProperties properties, final Clock clock) {
        this.properties = properties;
        this.clock = clock;
        final byte[] secret = properties.secretKey().getBytes(StandardCharsets.UTF_8);
        // jjwt와 같게 secret 길이가 허용하는 HS 알고리즘만 받는다(32바이트 HS256, 48 HS384, 64 이상 HS512).
        this.acceptedAlgorithms = MACSigner.getCompatibleAlgorithms(secret.length * 8);
        try {
            // 32바이트(HS256) 미만이면 KeyLengthException으로 기동을 막는다.
            this.verifier = new MACVerifier(secret);
        } catch (final JOSEException exception) {
            throw new IllegalArgumentException("security.admission.secret-key must be at least 32 bytes", exception);
        }
    }

    /**
     * 공개 진입점. 검증 실패는 admission 예외로 그대로 나간다 — 예외가 HTTP 상태와 E-code를 스스로 들고 있으므로 여기서 다시 번역하지 않는다. 대기열이 필요한지는 호출자가 이미
     * 판단했으므로 여기서 회차 정책을 조회하지 않는다.
     */
    @Override
    public void verify(final long performanceId, final long memberId, final @Nullable String admissionToken) {
        if (!properties.enforcementEnabled()) {
            return;
        }
        if (admissionToken == null || admissionToken.isBlank()) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8000, "admission token missing");
        }
        final JWTClaimsSet claims = parse(admissionToken);
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

    /** 서명 → 만료 → nbf → issuer 순서로 본다. jjwt 파서와 같은 순서라 만료이면서 issuer가 틀린 토큰도 만료로 거부된다. */
    private JWTClaimsSet parse(final String token) {
        final JWTClaimsSet claims;
        try {
            final SignedJWT jwt = SignedJWT.parse(token);
            if (!acceptedAlgorithms.contains(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                throw invalid(null);
            }
            claims = jwt.getJWTClaimsSet();
            // Nimbus는 숫자 sub를 문자열로 바꿔 읽지만 jjwt는 claim 해석 단계에서 거부했다.
            final Object rawSubject = jwt.getPayload().toJSONObject().get("sub");
            if (rawSubject != null && !(rawSubject instanceof String)) {
                throw invalid(null);
            }
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw invalid(exception);
        }
        final Date now = Date.from(clock.instant());
        if (claims.getExpirationTime() != null && now.after(claims.getExpirationTime())) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8001, "admission token expired");
        }
        if ((claims.getNotBeforeTime() != null && now.before(claims.getNotBeforeTime()))
                || !properties.issuer().equals(claims.getIssuer())) {
            throw invalid(null);
        }
        return claims;
    }

    private static AdmissionTokenException invalid(final @Nullable Exception cause) {
        return new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid", cause);
    }

    private void validateAudience(final JWTClaimsSet claims) {
        if (!claims.getAudience().contains(properties.audience())) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid audience");
        }
    }

    private void validateScope(final JWTClaimsSet claims) {
        if (!SCOPE.equals(claims.getClaim(SCOPE_CLAIM))) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid scope");
        }
    }

    private void validateTimestamps(final JWTClaimsSet claims) {
        if (claims.getIssueTime() == null || claims.getExpirationTime() == null) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid timestamps");
        }
    }

    private long parseMemberId(final JWTClaimsSet claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            throw new AdmissionTokenException(AdmissionErrorCode.E8002, "admission token invalid subject", exception);
        }
    }

    private long readLongClaim(final JWTClaimsSet claims, final String claimName) {
        Object value = claims.getClaim(claimName);
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
