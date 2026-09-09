package com.ticket.member.infrastructure;

import com.ticket.member.domain.Member;
import com.ticket.member.domain.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SpringDataMemberJpaRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail_EmailAndDeletedAtIsNull(String email);

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    /**
     * 소셜 계정 조건으로 회원을 찾는다. 자식(소셜 계정)과 부모(회원)의 {@code deletedAt} 조건을
     * 둘 다 확인한다 — 탈퇴한 회원의 계정이나 연결 해제된 계정으로는 로그인되지 않아야 한다.
     */
    @Query("""
            SELECT m
            FROM Member m
            JOIN m.socialAccounts msa
            WHERE msa.socialProvider = :socialProvider
              AND msa.socialId = :socialId
              AND msa.deletedAt IS NULL
              AND m.deletedAt IS NULL
            """)
    Optional<Member> findActiveBySocialAccount(
            @Param("socialProvider") SocialProvider socialProvider,
            @Param("socialId") String socialId
    );
}
