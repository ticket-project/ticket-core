package com.ticket.member.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.RawPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.UnauthenticatedException;

import lombok.RequiredArgsConstructor;

/** 이메일·비밀번호 자격 증명 인증 하나의 책임만 진다. 회원 등록은 {@link MemberRegistrar}가 별도로 맡는다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CredentialAuthenticator {
    private static final String TIMING_GUARD_DUMMY_PASSWORD = "timing-guard-dummy-password";
    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    public Member authenticate(final String email, final RawPassword password) {
        final Optional<Member> activeMember = memberRepository.findActiveByEmail(email);

        if (activeMember.isEmpty()) {
            // 타이밍 공격 방어: 회원이 없어도 해싱을 수행하여 응답 시간을 동일하게 유지
            passwordHasher.hash(RawPassword.create(TIMING_GUARD_DUMMY_PASSWORD));
            throw new UnauthenticatedException();
        }

        final Member member = activeMember.get();
        if (member.getEncodedPassword() == null
                || !passwordHasher.matches(password, member.getEncodedPassword())) {
            throw new UnauthenticatedException();
        }
        return member;
    }
}
