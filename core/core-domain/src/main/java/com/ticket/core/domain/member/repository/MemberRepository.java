package com.ticket.core.domain.member.repository;

import com.ticket.core.domain.member.model.Member;

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
}
