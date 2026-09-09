package com.ticket.member.infrastructure;

import com.ticket.member.application.AccessTokenReadResult;
import com.ticket.member.application.AccessTokenReader;
import com.ticket.member.AuthenticatedMember;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenCodec implements AccessTokenReader {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecretKey secretKey;

    @Autowired
    public JwtAccessTokenCodec(final JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    JwtAccessTokenCodec(final JwtProperties jwtProperties, final Clock clock) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(final Long memberId, final String role) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(memberId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * JJWT 예외를 중립 결과로 바꾼다. 라이브러리 예외 타입이 이 모듈 밖으로 나가지 않게 한다.
     */
    @Override
    public AccessTokenReadResult read(final String accessToken) {
        try {
            return AccessTokenReadResult.authenticated(parse(accessToken));
        } catch (final ExpiredJwtException exception) {
            return AccessTokenReadResult.expired();
        } catch (final JwtException | IllegalArgumentException exception) {
            return AccessTokenReadResult.invalid();
        }
    }

    private AuthenticatedMember parse(final String accessToken) {
        Claims claims = Jwts.parser()
                .requireIssuer(jwtProperties.getIssuer())
                .clock(() -> Date.from(clock.instant()))
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

        String subject = claims.getSubject();
        String role = claims.get(ROLE_CLAIM, String.class);
        if (subject == null || subject.isBlank() || role == null || role.isBlank() || claims.getExpiration() == null) {
            throw new IllegalArgumentException("JWT required claim is missing");
        }

        return new AuthenticatedMember(Long.parseLong(subject), role);
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpirationSeconds();
    }
}
