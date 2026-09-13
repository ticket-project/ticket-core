package com.ticket.member.auth.web.request;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.ticket.member.auth.application.usecase.LoginUseCase;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @JsonAlias({"id", "loginId"})
    @NotBlank
    private String email;

    @NotBlank private String password;

    public LoginUseCase.Input toInput() {
        return new LoginUseCase.Input(email, password);
    }
}
