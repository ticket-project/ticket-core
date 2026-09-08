/**
 * Member BC: Member, MemberSocialAccount, 이메일·소셜 로그인, OAuth2 provider adapter, 비밀번호,
 * access/refresh token, 회원 상태와 탈퇴, 전역 Spring Security filter chain을 소유한다.
 *
 * <p>전역 {@code SecurityFilterChain}은 member가 제공한다({@code infrastructure.security.
 * SecurityConfig}) — 인증·인가 해석이 결국 member 소유이기 때문이다.
 *
 * 공개 계약:
 * - {@link com.ticket.member.AuthenticatedMember} (다른 module controller가 parameter로 받는
 *   인증 principal — memberId와 role만 가진다)
 * - {@link com.ticket.member.MemberLookup} / {@link com.ticket.member.MemberStatus} (entity 대신
 *   쓰는 회원 조회·활성 검증 계약)
 * - {@link com.ticket.member.AccessTokenAuthenticator} (booking의 WebSocket 인증이 원본 access
 *   token 문자열을 검증할 때 쓴다)
 *
 * <p>찜(showlike)의 데이터는 이 module이 아니라 favorite module이 소유한다. 찜하기/찜 해제 시
 * 회원 활성 확인({@link com.ticket.member.MemberLookup#requireActive})은 favorite가 아니라 show의
 * use case(예: {@code AddShowLikeUseCase}, {@code RemoveShowLikeUseCase})가 이 module의 공개
 * 계약을 호출해 수행한다 — favorite는 업무 module 의존이 없는 leaf다(ADR 0006 §2).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Member", allowedDependencies = {})
package com.ticket.member;
