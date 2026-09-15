package com.ticket.security.oauth;

import java.util.Objects;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeOAuth2TokenRequest {
    @NotBlank private @Nullable String code;

    public ExchangeOAuth2TokenUseCase.Input toInput() {
        // @Valid가 @NotBlank를 먼저 통과시킨 뒤에만 호출되므로 code는 여기서 null일 수 없다.
        return new ExchangeOAuth2TokenUseCase.Input(
                Objects.requireNonNull(code, "code must not be null"));
    }
}
