package com.ticket.member.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.MemberStatus;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.MemberNotFoundException;

import lombok.RequiredArgsConstructor;

/** 토큰이 가리키는 회원이 지금도 활성 상태인지 확인한다. */
@Service
@RequiredArgsConstructor
public class GetActiveMemberIdentityUseCase {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberStatus execute(final long memberId) {
        final Member member =
                memberRepository.findActiveById(memberId).orElseThrow(() -> new MemberNotFoundException());
        return new MemberStatus(
                member.getId(), !member.isDeleted(), member.getRole().name());
    }
}
