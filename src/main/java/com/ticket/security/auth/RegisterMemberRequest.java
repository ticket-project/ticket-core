package com.ticket.security.auth;

import java.util.Objects;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterMemberRequest {
    @JsonAlias({"id", "loginId"})
    @NotBlank
    private @Nullable String email;

    @NotBlank private @Nullable String password;
    @NotBlank private @Nullable String name;

    public RegisterMemberUseCase.Input toInput() {
        // @Valid가 @NotBlank를 먼저 통과시킨 뒤에만 호출되므로 세 값은 여기서 null일 수 없다.
        return new RegisterMemberUseCase.Input(
                Objects.requireNonNull(email, "email must not be null"),
                Objects.requireNonNull(password, "password must not be null"),
                Objects.requireNonNull(name, "name must not be null"));
    }
}
