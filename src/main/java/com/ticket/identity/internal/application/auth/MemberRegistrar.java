package com.ticket.identity.internal.application.auth;

import com.ticket.core.support.exception.CoreException;
import com.ticket.identity.internal.application.auth.password.PasswordHasher;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import com.ticket.identity.internal.domain.member.model.Email;
import com.ticket.identity.internal.domain.member.model.RawPassword;
import com.ticket.identity.internal.domain.member.model.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 하나의 책임만 진다. 비밀번호 해싱과 이메일 중복 등록 판단을 맡고,
 * 로그인 자격 증명 인증은 {@link CredentialAuthenticator}가 별도로 맡는다.
 */
@Service
@RequiredArgsConstructor
public class MemberRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistrar.class);
    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public Long register(final Email email, final RawPassword rawPassword, final String name) {
        final Member member = new Member(
                email,
                passwordHasher.hash(rawPassword),
                name,
                Role.MEMBER
        );

        try {
            return memberRepository.save(member).getId();
        } catch (DataIntegrityViolationException e) {
            log.warn("이메일 중복 회원가입 시도: {}", email);
            throw new CoreException(ErrorType.MEMBER_DUPLICATE_EMAIL);
        }
    }
}
