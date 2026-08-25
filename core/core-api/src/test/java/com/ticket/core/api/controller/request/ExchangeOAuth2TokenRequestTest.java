package com.ticket.core.api.controller.request;

import com.ticket.core.app.auth.command.ExchangeOAuth2TokenUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeOAuth2TokenRequestTest {

    @Test
    void mapsToUseCaseInput() {
        ExchangeOAuth2TokenRequest request = new ExchangeOAuth2TokenRequest("oauth-code");

        ExchangeOAuth2TokenUseCase.Input input = request.toInput();

        assertThat(input.code()).isEqualTo("oauth-code");
    }
}
