package com.ticket.member.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.SocialAccountSnapshot;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.MemberNotFoundException;

import lombok.RequiredArgsConstructor;

/** 회원과 소셜 연결을 함께 탈퇴 처리한다. */
@Service
@RequiredArgsConstructor
public class WithdrawMemberUseCase {
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public List<SocialAccountSnapshot> execute(final long memberId) {
        final LocalDateTime now = LocalDateTime.now(clock);
        final Member member =
                memberRepository.findActiveById(memberId).orElseThrow(() -> new MemberNotFoundException());
        final List<SocialAccountSnapshot> socialAccounts = member.activeSocialAccounts().stream()
                .map(account -> new SocialAccountSnapshot(account.getSocialProvider(), account.getSocialId()))
                .toList();

        member.withdraw(now);

        return socialAccounts;
    }
}
