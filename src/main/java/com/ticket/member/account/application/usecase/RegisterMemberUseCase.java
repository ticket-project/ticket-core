package com.ticket.member.account.application.usecase;

import com.ticket.error.InvalidRequestException;
import com.ticket.member.account.application.MemberRegistrar;
import com.ticket.member.account.domain.Email;
import com.ticket.member.auth.domain.RawPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
            if (email == null || email.isBlank()) {
                throw new InvalidRequestException("email는 필수입니다.");
            }
            if (password == null || password.isBlank()) {
                throw new InvalidRequestException("password는 필수입니다.");
            }
            if (name == null || name.isBlank()) {
                throw new InvalidRequestException("name는 필수입니다.");
            }
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
