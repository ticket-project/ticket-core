package com.ticket.member.account.web.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.ticket.member.account.application.usecase.RegisterMemberUseCase;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank
    private String password;

    @NotBlank
    private String name;

    public RegisterMemberUseCase.Input toInput() {
        return new RegisterMemberUseCase.Input(email, password, name);
    }
}
