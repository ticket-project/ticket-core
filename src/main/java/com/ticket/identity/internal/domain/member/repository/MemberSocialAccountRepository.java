package com.ticket.identity.internal.domain.member.repository;

import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.model.MemberSocialAccount;
import com.ticket.identity.internal.domain.member.model.SocialProvider;

import java.util.List;
import java.util.Optional;

/**
 * 소셜 계정 연결 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 */
public interface MemberSocialAccountRepository {

    MemberSocialAccount save(MemberSocialAccount socialAccount);

    Optional<MemberSocialAccount> findActiveBySocialProviderAndSocialId(
            SocialProvider socialProvider,
            String socialId
    );

    Optional<MemberSocialAccount> findActiveByMemberAndProvider(Member member, SocialProvider socialProvider);

    List<MemberSocialAccount> findAllActiveByMember(Member member);
}
