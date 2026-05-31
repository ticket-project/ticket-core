package com.ticket.support.security.internalauth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalAuthTokenServiceTest {

    private static final String ISSUER = "ticket-gateway";
    private static final String AUDIENCE = "ticket-queue";
    private static final String SECRET_KEY = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-05-29T00:00:00Z");

    @Test
    void issueAndVerifyInternalAuthToken() {
        InternalAuthTokenService service = internalAuthTokenService(NOW, AUDIENCE, 60L);

        String token = service.issue(7L, "MEMBER");

        InternalAuthClaims claims = service.verify(token);
        assertThat(claims.memberId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo("MEMBER");
        assertThat(claims.audience()).isEqualTo(AUDIENCE);
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(60L));
        assertThat(claims.tokenId()).isNotBlank();
    }

    @Test
    void rejectExpiredInternalAuthToken() {
        InternalAuthTokenService issuer = internalAuthTokenService(NOW, AUDIENCE, 60L);
        InternalAuthTokenService verifier = internalAuthTokenService(NOW.plusSeconds(61L), AUDIENCE, 60L);
        String token = issuer.issue(7L, "MEMBER");

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InternalAuthTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectAudienceMismatch() {
        InternalAuthTokenService issuer = internalAuthTokenService(NOW, "ticket-core", 60L);
        InternalAuthTokenService queueVerifier = internalAuthTokenService(NOW, "ticket-queue", 60L);
        String token = issuer.issue(7L, "MEMBER");

        assertThatThrownBy(() -> queueVerifier.verify(token))
                .isInstanceOf(InternalAuthTokenException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void rejectTamperedInternalAuthToken() {
        InternalAuthTokenService service = internalAuthTokenService(NOW, AUDIENCE, 60L);
        String token = service.issue(7L, "MEMBER");
        String[] parts = token.split("\\.");
        char replacement = parts[1].charAt(0) == 'a' ? 'b' : 'a';
        String tamperedToken = parts[0] + "." + replacement + parts[1].substring(1) + "." + parts[2];

        assertThatThrownBy(() -> service.verify(tamperedToken))
                .isInstanceOf(InternalAuthTokenException.class)
                .hasMessageContaining("invalid");
    }

    private InternalAuthTokenService internalAuthTokenService(
            final Instant now,
            final String audience,
            final long expirationSeconds
    ) {
        InternalAuthTokenProperties properties = new InternalAuthTokenProperties(
                ISSUER,
                audience,
                SECRET_KEY,
                expirationSeconds
        );
        return new InternalAuthTokenService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
