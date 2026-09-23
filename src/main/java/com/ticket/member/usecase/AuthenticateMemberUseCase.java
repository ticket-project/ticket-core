package com.ticket.member.usecase;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.UnauthenticatedException;

import lombok.RequiredArgsConstructor;

/** 없는 계정과 비밀번호 불일치를 같은 실패와 비슷한 해싱 비용으로 처리한다. */
@Service
@RequiredArgsConstructor
public class AuthenticateMemberUseCase {
    private static final String TIMING_GUARD_DUMMY_PASSWORD = "timing-guard-dummy-password";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MemberStatus execute(final String email, final RawPassword password) {
        final Optional<Member> activeMember = memberRepository.findActiveByEmail(email);

        if (activeMember.isEmpty()) {
            passwordEncoder.encode(TIMING_GUARD_DUMMY_PASSWORD);
            throw new UnauthenticatedException();
        }

        final Member member = activeMember.get();
        if (member.getEncodedPassword() == null
                || !passwordEncoder.matches(
                        password.getPassword(), member.getEncodedPassword().getPassword())) {
            throw new UnauthenticatedException();
        }
        return new MemberStatus(
                member.getId(), !member.isDeleted(), member.getRole().name());
    }
}
