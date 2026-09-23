package com.ticket.member.usecase;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.RawPassword;
import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.member.exception.DuplicateEmailException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 회원 계정 생성과 비밀번호 해시 저장을 한 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterMemberUseCase {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long execute(final String email, final RawPassword password, final String name) {
        final Email createdEmail = Email.create(email);
        final Member member = new Member(
                createdEmail,
                EncodedPassword.create(passwordEncoder.encode(password.getPassword())),
                name,
                Role.MEMBER);

        try {
            return memberRepository.save(member).getId();
        } catch (final DataIntegrityViolationException exception) {
            // 이메일 주소 자체는 남기지 않는다.
            log.warn("이메일 중복 회원가입 시도: {}", createdEmail);
            throw new DuplicateEmailException();
        }
    }
}
