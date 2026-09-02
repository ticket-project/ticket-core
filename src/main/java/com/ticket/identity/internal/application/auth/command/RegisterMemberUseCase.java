package com.ticket.identity.internal.application.auth.command;

import com.ticket.identity.internal.application.auth.MemberRegistrar;
import com.ticket.identity.internal.domain.member.model.Email;
import com.ticket.identity.internal.domain.member.model.RawPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class RegisterMemberUseCase {

    private final MemberRegistrar memberRegistrar;

    /**
     * Email과 RawPassword의 형식 정책은 도메인 값 객체가 소유한다. 여기서는 어느 adapter에서
     * 호출해도 성립해야 하는 "필수 값이 왔는가"만 판정한다.
     */
    public record Input(
            String email,
            String password,
            String name
    ) {
        public Input {
            RequiredInput.notBlank(email, "email");
            RequiredInput.notBlank(password, "password");
            RequiredInput.notBlank(name, "name");
        }
    }
    public record Output(Long memberId) {}

    public Output execute(final Input input) {
        return new Output(memberRegistrar.register(
                Email.create(input.email()),
                RawPassword.create(input.password()),
                input.name()
        ));
    }
}
