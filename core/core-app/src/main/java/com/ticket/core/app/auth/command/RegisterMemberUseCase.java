package com.ticket.core.app.auth.command;

import com.ticket.core.app.auth.AuthService;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.RawPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterMemberUseCase {

    private final AuthService authService;

    public record Input(
            String email,
            String password,
            String name
    ) {}
    public record Output(Long memberId) {}

    public Output execute(final Input input) {
        return new Output(authService.register(
                Email.create(input.email()),
                RawPassword.create(input.password()),
                input.name()
        ));
    }
}
