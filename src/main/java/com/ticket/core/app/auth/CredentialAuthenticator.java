package com.ticket.core.app.auth;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.auth.password.PasswordHasher;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.member.model.RawPassword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 이메일·비밀번호 자격 증명 인증 하나의 책임만 진다. 회원 등록은
 * {@link MemberRegistrar}가 별도로 맡는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CredentialAuthenticator {

    private static final String TIMING_GUARD_DUMMY_PASSWORD = "timing-guard-dummy-password";

    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    public Member authenticate(final String email, final String password) {
        final Optional<Member> optMember = memberRepository.findActiveByEmail(email);

        if (optMember.isEmpty()) {
            // 타이밍 공격 방어: 회원이 없어도 해싱을 수행하여 응답 시간을 동일하게 유지
            passwordHasher.hash(RawPassword.create(TIMING_GUARD_DUMMY_PASSWORD));
            throw new CoreException(ApplicationErrorType.AUTHENTICATION_FAILED);
        }

        final Member foundMember = optMember.get();
        if (foundMember.getEncodedPassword() == null || !passwordHasher.matches(RawPassword.create(password), foundMember.getEncodedPassword())) {
            throw new CoreException(ApplicationErrorType.AUTHENTICATION_FAILED);
        }
        return foundMember;
    }
}
