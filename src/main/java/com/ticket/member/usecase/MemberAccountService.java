package com.ticket.member.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.api.SocialIdentity;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.MemberNotFoundException;

import lombok.RequiredArgsConstructor;

/** 회원 활성 상태를 확인하고 소셜 연결·탈퇴 연산을 각 트랜잭션 서비스에 연결한다. */
@Service
@RequiredArgsConstructor
public class MemberAccountService implements MemberAccountApi {
    private final MemberRepository memberRepository;
    private final SocialAccountProvisioningService socialAccountProvisioningService;
    private final WithdrawMemberUseCase withdrawMemberUseCase;

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedMember getActiveIdentity(final long memberId) {
        final Member member = memberRepository.findActiveById(memberId).orElseThrow(MemberNotFoundException::new);
        return identityOf(member);
    }

    @Override
    public AuthenticatedMember resolveSocialAccount(final SocialIdentity identity) {
        final Member member = socialAccountProvisioningService.getOrCreateMember(identity);
        return identityOf(member);
    }

    @Override
    public List<SocialAccountSnapshot> withdraw(final long memberId) {
        return withdrawMemberUseCase.execute(memberId);
    }

    private AuthenticatedMember identityOf(final Member member) {
        return new AuthenticatedMember(member.getId(), member.getRole().name());
    }
}
