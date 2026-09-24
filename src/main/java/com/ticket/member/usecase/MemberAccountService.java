package com.ticket.member.usecase;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.MemberNotFoundException;

import lombok.RequiredArgsConstructor;

/** 회원 자격 증명과 활성 상태를 확인하고 소셜 연결·탈퇴 연산을 각 트랜잭션 서비스에 연결한다. */
@Service
@RequiredArgsConstructor
public class MemberAccountService implements MemberAccountApi {
    private static final String TIMING_GUARD_DUMMY_PASSWORD = "timing-guard-dummy-password";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2MemberProvisioningService oauth2MemberProvisioningService;
    private final WithdrawMemberUseCase withdrawMemberUseCase;

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberStatus> authenticate(final String email, final RawPassword password) {
        final Optional<Member> activeMember = memberRepository.findActiveByEmail(email);

        if (activeMember.isEmpty()) {
            passwordEncoder.encode(TIMING_GUARD_DUMMY_PASSWORD);
            return Optional.empty();
        }

        final Member member = activeMember.get();
        if (member.getEncodedPassword() == null
                || !passwordEncoder.matches(
                        password.getPassword(), member.getEncodedPassword().getPassword())) {
            return Optional.empty();
        }
        return Optional.of(statusOf(member));
    }

    @Override
    @Transactional(readOnly = true)
    public MemberStatus getActiveIdentity(final long memberId) {
        final Member member = memberRepository.findActiveById(memberId).orElseThrow(MemberNotFoundException::new);
        return statusOf(member);
    }

    @Override
    public MemberStatus resolveSocialAccount(final SocialIdentity identity) {
        final Member member = oauth2MemberProvisioningService.getOrCreateMember(identity);
        return statusOf(member);
    }

    @Override
    public List<SocialAccountSnapshot> withdraw(final long memberId) {
        return withdrawMemberUseCase.execute(memberId);
    }

    private MemberStatus statusOf(final Member member) {
        return new MemberStatus(
                member.getId(), !member.isDeleted(), member.getRole().name());
    }
}
