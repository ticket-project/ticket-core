package com.ticket.identity.internal.infrastructure.member;

import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link MemberRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final SpringDataMemberJpaRepository jpaRepository;

    @Override
    public Member save(final Member member) {
        return jpaRepository.save(member);
    }

    @Override
    public Optional<Member> findActiveByEmail(final String email) {
        return jpaRepository.findByEmail_EmailAndDeletedAtIsNull(email);
    }

    @Override
    public Optional<Member> findActiveById(final Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsActiveById(final Long id) {
        return jpaRepository.existsByIdAndDeletedAtIsNull(id);
    }
}
