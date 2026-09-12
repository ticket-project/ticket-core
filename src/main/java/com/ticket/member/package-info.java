/**
 * Member BC: Member, MemberSocialAccount, 이메일·소셜 로그인, OAuth2 provider adapter, 비밀번호,
 * access/refresh token, 회원 상태와 탈퇴를 소유한다. 전역 HTTP 접근 정책과 SecurityContext 구성은
 * 별도 security 기술 모듈이 소유한다.
 *
 * 공개 계약:
 * - {@link com.ticket.member.AuthenticatedMember} (다른 module controller가 parameter로 받는
 *   인증 principal — memberId와 role만 가진다)
 * - {@link com.ticket.member.MemberLookup} / {@link com.ticket.member.MemberStatus} (entity 대신
 *   쓰는 회원 조회·활성 검증 계약)
 * - {@link com.ticket.member.AccessTokenAuthenticator} (booking의 WebSocket 인증이 원본 access
 *   token 문자열을 검증할 때 쓴다)
 * - {@link com.ticket.member.AccessTokenReader} / {@link com.ticket.member.AccessTokenReadResult}
 *   (security가 HTTP 인증 실패의 만료/무효 구분을 보존하며 access token을 검증할 때 쓴다)
 *
 * <p>찜(Like)의 데이터·HTTP endpoint·use case는 이 module이 아니라 like module(옛 favorite)이
 * 소유한다. 찜하기/찜 해제/찜 상태 조회 시 회원 활성 확인({@link
 * com.ticket.member.MemberLookup#requireActive})은 like가 이 module의 공개 계약을 직접 호출해
 * 수행한다 — JWT 인증만으로는 탈퇴 회원을 걸러낼 수 없어서다(인증 필터는 서명·만료만 보고,
 * 탈퇴가 access token을 무효화하지 않는다). {@code like -> member}는 순환을 만들지 않는다 —
 * member는 어떤 업무 module도 참조하지 않는 leaf이기 때문이다(ADR 0006 §2, ADR 0008).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Member", allowedDependencies = {"shared :: *"})
package com.ticket.member;
