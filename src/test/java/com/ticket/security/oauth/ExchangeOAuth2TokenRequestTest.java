package com.ticket.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExchangeOAuth2TokenRequestTest {
    @Test
    void mapsToUseCaseInput() {
        ExchangeOAuth2TokenRequest request = new ExchangeOAuth2TokenRequest("oauth-code");

        ExchangeOAuth2TokenUseCase.Input input = request.toInput();

        assertThat(input.code()).isEqualTo("oauth-code");
    }
}
