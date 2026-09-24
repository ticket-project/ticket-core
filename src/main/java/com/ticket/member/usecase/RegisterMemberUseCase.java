package com.ticket.member.usecase;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.domain.Email;
import com.ticket.member.domain.EncodedPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.domain.Role;
import com.ticket.member.exception.DuplicateEmailException;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 회원 계정 생성과 비밀번호 해시 저장을 한 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterMemberUseCase {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

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

    @Transactional
    public Output execute(final Input input) {
        final Email createdEmail = Email.create(input.email());
        final Member member = new Member(
                createdEmail,
                EncodedPassword.create(passwordEncoder.encode(input.password())),
                input.name(),
                Role.MEMBER);

        try {
            return new Output(memberRepository.save(member).getId());
        } catch (final DataIntegrityViolationException exception) {
            // 이메일 주소 자체는 남기지 않는다.
            log.warn("이메일 중복 회원가입 시도: {}", createdEmail);
            throw new DuplicateEmailException();
        }
    }
}
