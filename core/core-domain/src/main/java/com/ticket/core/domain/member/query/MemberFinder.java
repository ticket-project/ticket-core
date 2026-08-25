package com.ticket.core.domain.member.query;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import com.ticket.support.error.ErrorType;
import com.ticket.support.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberFinder {

    private final MemberRepository memberRepository;

    public Member findActiveMemberById(final Long id) {
        return memberRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(ErrorType.NOT_FOUND_DATA));
    }

    public void ensureActiveMemberExists(final Long id) {
        if (memberRepository.existsByIdAndDeletedAtIsNull(id)) {
            return;
        }
        throw new NotFoundException(ErrorType.NOT_FOUND_DATA);
    }
}
