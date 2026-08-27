package com.ticket.core.app.auth;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.auth.PasswordService;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.RawPassword;
import com.ticket.core.domain.member.model.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final MemberRepository memberRepository;
    private final PasswordService passwordService;

    @Transactional
    public Long register(final Email email, final RawPassword rawPassword, final String name) {
        final Member member = new Member(
                email,
                EncodedPassword.create(passwordService.encode(rawPassword.getPassword())),
                name,
                Role.MEMBER
        );

        try {
            return memberRepository.save(member).getId();
        } catch (DataIntegrityViolationException e) {
            log.warn("이메일 중복 회원가입 시도: {}", email);
            throw new CoreException(ApplicationErrorType.MEMBER_DUPLICATE_EMAIL);
        }
    }

    public Member login(final String email, final String password) {
        final Optional<Member> optMember = memberRepository.findActiveByEmail(email);

        if (optMember.isEmpty()) {
            // 타이밍 공격 방어: 회원이 없어도 해싱을 수행하여 응답 시간을 동일하게 유지
            passwordService.encode("timing-guard-dummy-password");
            throw new CoreException(ApplicationErrorType.AUTHENTICATION_FAILED);
        }

        final Member foundMember = optMember.get();
        if (foundMember.getEncodedPassword() == null || !passwordService.matches(RawPassword.create(password), foundMember.getEncodedPassword())) {
            throw new CoreException(ApplicationErrorType.AUTHENTICATION_FAILED);
        }
        return foundMember;
    }
}
