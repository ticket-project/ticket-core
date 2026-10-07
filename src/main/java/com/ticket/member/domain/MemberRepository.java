package com.ticket.member.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.ticket.member.api.SocialProvider;

/**
 * 회원 aggregate의 저장과 복원을 담당하는 도메인 Repository다. 구현은 Spring Data JPA가 만든다.
 *
 * <p>탈퇴한 회원은 조회 대상에서 제외한다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다. 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일 수도 있다.
 *
 * <p>소셜 계정({@code MemberSocialAccount})은 회원 aggregate의 자식이라 자기 Repository를 갖지 않는다. 자식을 이미 손에 쥔 조회는 {@code Member}의 컬렉션
 * 접근 메서드로 하고, 아래 {@link #findActiveBySocialAccount}처럼 <b>자식 속성으로 Root를 찾는 조회만</b> 이 Repository가 맡는다 — Repository가 반환하는
 * 것은 언제나 aggregate root다.
 */
public interface MemberRepository extends Repository<Member, Long> {
    <S extends Member> S save(S member);

    default Optional<Member> findActiveByEmail(final String email) {
        return findByEmail_EmailAndDeletedAtIsNull(email);
    }

    Optional<Member> findByEmail_EmailAndDeletedAtIsNull(String email);

    default Optional<Member> findActiveById(final Long id) {
        return findByIdAndDeletedAtIsNull(id);
    }

    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    /** 소셜 로그인 진입점이다. 회원과 소셜 계정 <b>양쪽 모두</b> 탈퇴 처리되지 않은 경우에만 찾는다 — 탈퇴한 회원의 계정이나 연결 해제된 계정으로는 로그인되지 않아야 한다. */
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
            @Param("socialProvider") SocialProvider socialProvider, @Param("socialId") String socialId);
}
