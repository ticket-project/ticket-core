package com.ticket.security.auth;

import org.springframework.stereotype.Service;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.RawPassword;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegisterMemberUseCase {
    private final MemberAccountApi memberAccountApi;

    /** Email과 RawPassword의 형식 정책은 도메인 값 객체가 소유한다. 여기서는 어느 adapter에서 호출해도 성립해야 하는 "필수 값이 왔는가"만 판정한다. */
    public record Input(String email, String password, String name) {
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
        return new Output(memberAccountApi.register(input.email(), RawPassword.create(input.password()), input.name()));
    }
}
