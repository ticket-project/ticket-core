package com.ticket.core.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticket.support.passport.Passport;
import com.ticket.support.token.passport.PassportService;
import com.ticket.support.token.passport.PassportTokenProperties;
import com.ticket.support.token.passport.PassportTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@SuppressWarnings("NonAsciiCharacters")
class CorePassportVerifierTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String ISSUER = "ticket-gateway";
    private static final String AUDIENCE = "ticket-core";
    private static final String SECRET_KEY = "abcdefabcdefabcdefabcdefabcdef12";

    @Test
    void verify는_유효한_passport_header를_Passport로_변환한다() {
        CorePassportVerifier verifier = new CorePassportVerifier(passportService(NOW));

        Passport passport = verifier.verify("Bearer " + issueToken(NOW));

        assertThat(passport.memberId()).isEqualTo(7L);
        assertThat(passport.role()).isEqualTo("MEMBER");
    }

    @Test
    void verify는_깨진_token이면_reason_invalid로_401을_던진다() {
        CorePassportVerifier verifier = new CorePassportVerifier(passportService(NOW));

        assertThatThrownBy(() -> verifier.verify("Bearer not-a-token"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("invalid");
    }

    @Test
    void verify는_bearer_형식이_아니면_reason_invalid로_401을_던진다() {
        CorePassportVerifier verifier = new CorePassportVerifier(passportService(NOW));

        assertThatThrownBy(() -> verifier.verify("passport-token"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("invalid");
    }

    @Test
    void verify는_만료된_token이면_reason_expired로_401을_던진다() {
        CorePassportVerifier verifier = new CorePassportVerifier(passportService(NOW.plusSeconds(61L)));

        assertThatThrownBy(() -> verifier.verify("Bearer " + issueToken(NOW)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("expired");
    }

    private PassportService passportService(final Instant clockInstant) {
        return new PassportService(
                new PassportTokenProperties(ISSUER, AUDIENCE, SECRET_KEY, 60L),
                Clock.fixed(clockInstant, ZoneOffset.UTC)
        );
    }

    private String issueToken(final Instant issuedAt) {
        PassportTokenService service = new PassportTokenService(
                new PassportTokenProperties(ISSUER, AUDIENCE, SECRET_KEY, 60L),
                Clock.fixed(issuedAt, ZoneOffset.UTC)
        );
        return service.issue(7L, "MEMBER");
    }
}
