package com.ticket.core.infra.member;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.MemberSocialAccount;
import com.ticket.core.domain.member.model.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SpringDataMemberSocialAccountJpaRepository extends JpaRepository<MemberSocialAccount, Long> {

    @Query("""
            SELECT msa
            FROM MemberSocialAccount msa
            JOIN FETCH msa.member m
            WHERE msa.socialProvider = :socialProvider
              AND msa.socialId = :socialId
              AND msa.deletedAt IS NULL
              AND m.deletedAt IS NULL
            """)
    Optional<MemberSocialAccount> findActiveBySocialProviderAndSocialId(
            @Param("socialProvider") SocialProvider socialProvider,
            @Param("socialId") String socialId
    );

    Optional<MemberSocialAccount> findByMemberAndSocialProviderAndDeletedAtIsNull(
            Member member,
            SocialProvider socialProvider
    );

    List<MemberSocialAccount> findAllByMemberAndDeletedAtIsNull(Member member);
}
