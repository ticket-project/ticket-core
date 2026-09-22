package com.ticket.member.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.MemberLookupApi;
import com.ticket.member.api.MemberSnapshot;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.MemberNotFoundException;

import lombok.RequiredArgsConstructor;

/** {@link MemberLookupApi}의 member 소유 구현이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberLookupService implements MemberLookupApi {
    private final MemberRepository memberRepository;

    @Override
    public void requireActive(final long memberId) {
        if (!memberRepository.existsActiveById(memberId)) {
            throw new MemberNotFoundException(memberId);
        }
    }

    @Override
    public MemberSnapshot getProfile(final long memberId) {
        final Member member = findActiveOrThrow(memberId);
        return new MemberSnapshot(
                member.getId(), member.getName(), member.getEmail().getEmail());
    }

    private Member findActiveOrThrow(final long memberId) {
        return memberRepository.findActiveById(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
    }
}
