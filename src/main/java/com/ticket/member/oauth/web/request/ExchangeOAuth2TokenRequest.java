package com.ticket.member.oauth.web.request;

import com.ticket.member.oauth.application.usecase.ExchangeOAuth2TokenUseCase;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeOAuth2TokenRequest {

    @NotBlank
    private String code;

    public ExchangeOAuth2TokenUseCase.Input toInput() {
        return new ExchangeOAuth2TokenUseCase.Input(code);
    }
}
