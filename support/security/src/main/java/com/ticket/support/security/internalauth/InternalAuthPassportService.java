package com.ticket.support.security.internalauth;

import com.ticket.support.passport.Passport;
import java.time.Clock;
import java.util.Objects;

public class InternalAuthPassportService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final InternalAuthTokenService internalAuthTokenService;

    public InternalAuthPassportService(final InternalAuthTokenProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InternalAuthPassportService(final InternalAuthTokenProperties properties, final Clock clock) {
        this.internalAuthTokenService = new InternalAuthTokenService(
                Objects.requireNonNull(properties, "properties must not be null"),
                Objects.requireNonNull(clock, "clock must not be null")
        );
    }

    public Passport verifyBearer(final String internalAuthHeader) {
        InternalAuthClaims claims = internalAuthTokenService.verify(extractBearerToken(internalAuthHeader));
        return new Passport(claims.memberId(), claims.role());
    }

    private String extractBearerToken(final String internalAuthHeader) {
        if (internalAuthHeader == null || !internalAuthHeader.startsWith(BEARER_PREFIX)) {
            throw new InternalAuthTokenException("internal auth token invalid");
        }
        String token = internalAuthHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new InternalAuthTokenException("internal auth token invalid");
        }
        return token;
    }
}
