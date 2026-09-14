package com.ticket.security.auth;

import jakarta.validation.constraints.NotBlank;

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
    private String email;

    @NotBlank private String password;
    @NotBlank private String name;

    public RegisterMemberUseCase.Input toInput() {
        return new RegisterMemberUseCase.Input(email, password, name);
    }
}
