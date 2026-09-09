package com.ticket.member.application;

import com.ticket.error.NotFoundException;
import com.ticket.member.MemberLookup;
import com.ticket.member.MemberProfile;
import com.ticket.member.MemberStatus;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MemberLookup}의 member 소유 구현이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberLookupService implements MemberLookup {

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
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다. id=" + memberId));
    }
}
