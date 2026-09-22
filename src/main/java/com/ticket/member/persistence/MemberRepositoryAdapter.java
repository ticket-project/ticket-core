package com.ticket.member.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ticket.member.api.SocialProvider;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;

import lombok.RequiredArgsConstructor;

/** {@link MemberRepository}의 JPA 구현이다. */
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

    @Override
    public Optional<Member> findActiveBySocialAccount(final SocialProvider socialProvider, final String socialId) {
        return jpaRepository.findActiveBySocialAccount(socialProvider, socialId);
    }
}
