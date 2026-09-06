package com.ticket.member.infrastructure.auth.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    @Test
    void refresh_token_default_expiration_is_one_day() {
        JwtProperties properties = new JwtProperties();

        assertThat(properties.getRefreshTokenExpirationSeconds()).isEqualTo(86400L);
    }
}
