package com.ticket.security.jwt;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.token.AccessTokenReadResult;

/**
 * access token을 Nimbus JOSE로 서명·검증한다.
 *
 * <p>jjwt에서 옮기면서 동작을 그대로 맞췄다. 서명 알고리즘은 secret 길이로 정한다(32바이트 이상 HS256, 48 이상 HS384, 64 이상 HS512 — jjwt
 * {@code Keys.hmacShaKeyFor}와 같다). 검증은 이 secret 길이가 허용하는 HS 알고리즘만 받는다. 만료는 주입된 {@link Clock} 기준 허용 오차 0이고, {@code exp}와
 * 같은 순간까지는 유효하다. 만료 판정이 issuer 확인보다 먼저다.
 */
@Component
public class JwtAccessTokenCodec {
    private static final String ROLE_CLAIM = "role";
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final JWSAlgorithm signingAlgorithm;
    private final Set<JWSAlgorithm> acceptedAlgorithms;
    private final MACSigner signer;
    private final MACVerifier verifier;

    public JwtAccessTokenCodec(final JwtProperties jwtProperties, final Clock clock) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        final byte[] secret = jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8);
        this.acceptedAlgorithms = MACSigner.getCompatibleAlgorithms(secret.length * 8);
        this.signingAlgorithm = strongest(acceptedAlgorithms);
        try {
            // 32바이트(256bit) 미만이면 KeyLengthException으로 기동을 막는다.
            this.signer = new MACSigner(secret);
            this.verifier = new MACVerifier(secret);
        } catch (final JOSEException exception) {
            throw new IllegalArgumentException("security.jwt.secret-key must be at least 32 bytes", exception);
        }
    }

    public String createAccessToken(final Long memberId, final String role) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(memberId))
                .claim(ROLE_CLAIM, role)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(signingAlgorithm), claims);
        try {
            jwt.sign(signer);
        } catch (final JOSEException exception) {
            throw new IllegalStateException("access token signing failed", exception);
        }
        return jwt.serialize();
    }

    /** 라이브러리 예외를 중립 결과로 바꾼다. 라이브러리 예외 타입이 이 모듈 밖으로 나가지 않게 한다. */
    public AccessTokenReadResult read(final String accessToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(accessToken);
            if (!acceptedAlgorithms.contains(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return new AccessTokenReadResult.Invalid();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            // Nimbus는 숫자 sub를 문자열로 바꿔 읽지만 jjwt는 claim 해석 단계에서 거부했다. 원문 타입으로 같은 시점에 거부한다.
            Object rawSubject = jwt.getPayload().toJSONObject().get("sub");
            if (rawSubject != null && !(rawSubject instanceof String)) {
                return new AccessTokenReadResult.Invalid();
            }
            Date now = Date.from(clock.instant());
            if (claims.getExpirationTime() != null && now.after(claims.getExpirationTime())) {
                return new AccessTokenReadResult.Expired();
            }
            return new AccessTokenReadResult.Authenticated(toMember(claims, now));
        } catch (final ParseException | JOSEException | IllegalArgumentException exception) {
            return new AccessTokenReadResult.Invalid();
        }
    }

    private AuthenticatedMember toMember(final JWTClaimsSet claims, final Date now) throws ParseException {
        if (claims.getNotBeforeTime() != null && now.before(claims.getNotBeforeTime())) {
            throw new IllegalArgumentException("JWT is not yet valid");
        }
        if (!jwtProperties.getIssuer().equals(claims.getIssuer())) {
            throw new IllegalArgumentException("JWT issuer mismatch");
        }
        String subject = claims.getSubject();
        String role = claims.getStringClaim(ROLE_CLAIM);
        if (subject == null
                || subject.isBlank()
                || role == null
                || role.isBlank()
                || claims.getExpirationTime() == null) {
            throw new IllegalArgumentException("JWT required claim is missing");
        }

        return new AuthenticatedMember(Long.parseLong(subject), role);
    }

    private static JWSAlgorithm strongest(final Set<JWSAlgorithm> algorithms) {
        if (algorithms.contains(JWSAlgorithm.HS512)) {
            return JWSAlgorithm.HS512;
        }
        return algorithms.contains(JWSAlgorithm.HS384) ? JWSAlgorithm.HS384 : JWSAlgorithm.HS256;
    }
}
