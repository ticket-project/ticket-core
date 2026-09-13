package com.ticket.member.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class IssuedAuthTokensTest {
    @Test
    void toString은_accessToken과_refreshToken을_평문으로_노출하지_않는다() {
        IssuedAuthTokens issuedAuthTokens =
                new IssuedAuthTokens(
                        "access-token", "refresh-token", "Bearer", 1800L, 1209600L, 7L);

        assertThat(issuedAuthTokens.toString())
                .doesNotContain("access-token")
                .doesNotContain("refresh-token");
    }
}
