package com.ticket.member.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.member.api.MemberLookupApi;
import com.ticket.member.api.MemberProfile;
import com.ticket.member.api.MemberStatus;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.shared.exception.NotFoundException;

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
            throw new NotFoundException("회원을 찾을 수 없습니다. id=" + memberId);
        }
    }

    @Override
    public MemberStatus getStatus(final long memberId) {
        final Member member = findActiveOrThrow(memberId);
        return new MemberStatus(member.getId(), !member.isDeleted(), member.getRole().name());
    }

    @Override
    public MemberProfile getProfile(final long memberId) {
        final Member member = findActiveOrThrow(memberId);
        return new MemberProfile(member.getId(), member.getName(), member.getEmail().getEmail());
    }

    private Member findActiveOrThrow(final long memberId) {
        return memberRepository
                .findActiveById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다. id=" + memberId));
    }
}
