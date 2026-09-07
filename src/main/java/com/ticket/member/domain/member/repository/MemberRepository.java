package com.ticket.member.domain.member.repository;

import com.ticket.member.domain.member.model.Member;
import com.ticket.member.domain.member.model.SocialProvider;

import java.util.Optional;

/**
 * 회원 aggregate의 저장과 복원을 담당하는 도메인 Repository다.
 *
 * <p>탈퇴한 회원은 조회 대상에서 제외한다.
 *
 * <p>조회 결과가 없다는 사실만 알려 주고, 그것을 어떤 오류로 볼지는 호출하는 유스케이스가 정한다.
 * 맥락에 따라 인증 실패일 수도, not-found일 수도, 멱등 성공일 수도 있다.
 *
 * <p>소셜 계정({@code MemberSocialAccount})은 회원 aggregate의 자식이라 자기 Repository를 갖지
 * 않는다. 자식을 이미 손에 쥔 조회는 {@code Member}의 컬렉션 접근 메서드로 하고, 아래
 * {@link #findActiveBySocialAccount}처럼 <b>자식 속성으로 Root를 찾는 조회만</b> 이 Repository가
 * 맡는다 — Repository가 반환하는 것은 언제나 aggregate root다.
 */
public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findActiveByEmail(String email);

    Optional<Member> findActiveById(Long id);

    boolean existsActiveById(Long id);

    /**
     * 소셜 로그인 진입점이다. 회원과 소셜 계정 <b>양쪽 모두</b> 탈퇴 처리되지 않은 경우에만 찾는다.
     */
    Optional<Member> findActiveBySocialAccount(SocialProvider socialProvider, String socialId);
}
