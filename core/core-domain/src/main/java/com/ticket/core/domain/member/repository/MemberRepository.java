package com.ticket.core.domain.member.repository;

import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.member.model.Member;
import com.ticket.support.error.CoreException;

import java.util.Optional;

/**
 * 회원 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>탈퇴한 회원은 조회 대상에서 제외한다.
 */
public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findActiveByEmail(String email);

    Optional<Member> findActiveById(Long id);

    boolean existsActiveById(Long id);

    /**
     * 활성 회원을 반환하고, 없으면 도메인 오류를 던진다.
     */
    default Member getActiveById(final Long id) {
        return findActiveById(id)
                .orElseThrow(() -> new CoreException(DomainErrorType.DATA_NOT_FOUND));
    }

    /**
     * 활성 회원이 존재하는지 확인하고, 없으면 도메인 오류를 던진다.
     */
    default void requireActiveExists(final Long id) {
        if (!existsActiveById(id)) {
            throw new CoreException(DomainErrorType.DATA_NOT_FOUND);
        }
    }
}
