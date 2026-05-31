package com.ticket.support.security.internalauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticket.support.passport.Passport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InternalAuthPassportServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ISSUER = "ticket-gateway";
    private static final String AUDIENCE = "ticket-queue";
    private static final String SECRET_KEY = "abcdefabcdefabcdefabcdefabcdef12";

    @Test
    void verifyBearer는_유효한_internal_auth_header를_Passport로_변환한다() {
        InternalAuthPassportService verifier = newVerifier(AUDIENCE, NOW);
        String token = issueToken(AUDIENCE, NOW);

        Passport passport = verifier.verifyBearer("Bearer " + token);

        assertThat(passport.memberId()).isEqualTo(10L);
        assertThat(passport.role()).isEqualTo("MEMBER");
    }

    @Test
    void verifyBearer는_bearer_형식이_아니면_거부한다() {
        InternalAuthPassportService verifier = newVerifier(AUDIENCE, NOW);

        assertThatThrownBy(() -> verifier.verifyBearer("internal-token"))
                .isInstanceOf(InternalAuthTokenException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void verifyBearer는_만료된_token이면_거부한다() {
        InternalAuthPassportService verifier = newVerifier(AUDIENCE, NOW.plusSeconds(61L));
        String token = issueToken(AUDIENCE, NOW);

        assertThatThrownBy(() -> verifier.verifyBearer("Bearer " + token))
                .isInstanceOf(InternalAuthTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verifyBearer는_audience가_다르면_거부한다() {
        InternalAuthPassportService verifier = newVerifier("ticket-core", NOW);
        String token = issueToken(AUDIENCE, NOW);

        assertThatThrownBy(() -> verifier.verifyBearer("Bearer " + token))
                .isInstanceOf(InternalAuthTokenException.class)
                .hasMessageContaining("audience");
    }

    private InternalAuthPassportService newVerifier(final String audience, final Instant now) {
        return new InternalAuthPassportService(
                new InternalAuthTokenProperties(ISSUER, audience, SECRET_KEY, 60L),
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private String issueToken(final String audience, final Instant now) {
        InternalAuthTokenService service = new InternalAuthTokenService(
                new InternalAuthTokenProperties(ISSUER, audience, SECRET_KEY, 60L),
                Clock.fixed(now, ZoneOffset.UTC)
        );
        return service.issue(10L, "MEMBER");
    }
}
