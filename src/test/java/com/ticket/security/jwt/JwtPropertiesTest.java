package com.ticket.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {
    @Test
    void refresh_token_default_expiration_is_one_day() {
        JwtProperties properties = new JwtProperties();

        assertThat(properties.getRefreshTokenExpirationSeconds()).isEqualTo(86400L);
    }
}
