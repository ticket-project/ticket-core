package com.ticket.member.infrastructure.member;

import com.ticket.member.domain.member.model.Member;
import com.ticket.member.domain.member.model.MemberSocialAccount;
import com.ticket.member.domain.member.model.SocialProvider;
import com.ticket.member.domain.member.repository.MemberSocialAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link MemberSocialAccountRepository}의 JPA 구현이다.
 */
@Repository
@RequiredArgsConstructor
public class MemberSocialAccountRepositoryAdapter implements MemberSocialAccountRepository {

    private final SpringDataMemberSocialAccountJpaRepository jpaRepository;

    @Override
    public MemberSocialAccount save(final MemberSocialAccount socialAccount) {
        return jpaRepository.save(socialAccount);
    }

    @Override
    public Optional<MemberSocialAccount> findActiveBySocialProviderAndSocialId(
            final SocialProvider socialProvider,
            final String socialId
    ) {
        return jpaRepository.findActiveBySocialProviderAndSocialId(socialProvider, socialId);
    }

    @Override
    public Optional<MemberSocialAccount> findActiveByMemberAndProvider(
            final Member member,
            final SocialProvider socialProvider
    ) {
        return jpaRepository.findByMemberAndSocialProviderAndDeletedAtIsNull(member, socialProvider);
    }

    @Override
    public List<MemberSocialAccount> findAllActiveByMember(final Member member) {
        return jpaRepository.findAllByMemberAndDeletedAtIsNull(member);
    }
}
