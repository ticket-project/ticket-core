package com.ticket.core.domain.member.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberFinder {

    private final MemberRepository memberRepository;

    public Member findActiveMemberById(final Long id) {
        return memberRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND));
    }

    public void ensureActiveMemberExists(final Long id) {
        if (memberRepository.existsByIdAndDeletedAtIsNull(id)) {
            return;
        }
        throw new CoreException(DomainErrorType.DATA_NOT_FOUND);
    }
}
