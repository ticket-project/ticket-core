/**
 * Member BC: Member, MemberSocialAccount, 이메일·비밀번호 해시·역할·탈퇴 상태를 소유한다. 회원 테이블과 인증 데이터의 소유권은 계속 여기에 있다.
 *
 * <p>인증 흐름의 <b>조립</b>은 security가 한다 — 가입·로그인·갱신·로그아웃·탈퇴 절차, JWT 발급·검증, OAuth2 provider 통신과 응답 해석, refresh token 저장이
 * 그렇다. member는 그 절차가 필요로 하는 계정 연산만 공개 계약으로 제공한다. <b>비밀번호 해시는 이 module 밖으로 나가지 않는다</b> — 해싱과 일치 확인을 member가 직접 수행하므로
 * {@code member -> security} 의존이 없다.
 *
 * <p>공개 계약:
 *
 * <ul>
 *   <li>{@link com.ticket.member.api.MemberAccountApi} — 등록·자격 증명 확인·활성 확인·소셜 계정 해석·탈퇴. 오가는 값은
 *       {@link com.ticket.member.api.RawPassword}, {@link com.ticket.member.api.SocialIdentity},
 *       {@link com.ticket.member.api.SocialProvider}, {@link com.ticket.member.api.SocialAccountSnapshot}이다
 *   <li>{@link com.ticket.member.api.MemberLookupApi} / {@link com.ticket.member.api.MemberStatus} /
 *       {@link com.ticket.member.api.MemberSnapshot} — entity 대신 쓰는 회원 조회·활성 검증 계약
 *   <li>{@link com.ticket.member.api.AuthenticatedMember} — 다른 module controller가 parameter로 받는 인증 principal. memberId와
 *       role만 가진다
 * </ul>
 *
 * <p>찜(Like)의 데이터·HTTP endpoint·use case는 like module이 소유한다. 요청자의 활성 상태는 security가 이 module의
 * {@code MemberAccountApi.getActiveIdentity}로 인증 단계에서 확인한다. like가 회원 상태를 다시 조회하지 않는다.
 */
@NullMarked
@org.springframework.modulith.ApplicationModule(
        displayName = "Member",
        allowedDependencies = {"shared :: api", "shared :: web", "shared :: exception", "shared :: jpa"})
package com.ticket.member;

import org.jspecify.annotations.NullMarked;
