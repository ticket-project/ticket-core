/**
 * Identity module: Member, MemberSocialAccount, 이메일·소셜 로그인, OAuth2 provider adapter,
 * 비밀번호, access/refresh token, 회원 상태와 탈퇴, 전역 Spring Security filter chain을 소유한다.
 *
 * <p>구현은 모두 {@code internal} 아래에 있고, 이 module root에는 다른 module이 쓰는 공개 계약만
 * 둔다: {@link com.ticket.identity.AuthenticatedMember}(다른 module controller가 parameter로
 * 받는 인증 principal — memberId와 role만 가진다), {@link com.ticket.identity.MemberLookup}/
 * {@link com.ticket.identity.MemberStatus}(entity 대신 쓰는 회원 조회·활성 검증 계약),
 * {@link com.ticket.identity.IdentityMetadata}(metadata module이 조합할 Role/SocialProvider
 * code/label).
 *
 * <p>전역 {@code SecurityFilterChain}은 identity가 제공한다({@code internal.infrastructure.security.
 * SecurityConfig}) — 인증·인가 해석이 결국 identity 소유이기 때문이다. WebSocket 인증
 * ({@code WebSocketAuthInterceptor})은 booking이 아직 별도 module로 이동하지 않아(Task 7의 일)
 * legacy {@code com.ticket.core.config.security}에 남아 있고, identity가 internal로 옮긴
 * {@code AccessTokenReader}/{@code AccessTokenReadResult}를 그대로 참조하는 임시 결합이다. 같은
 * 이유로 legacy {@code order}·{@code showlike}·{@code performanceseat} 코드는 아직 identity
 * internal {@code Member}/{@code MemberRepository}를 직접 참조한다 — 그 module들이 각자의 Task로
 * 이동하며 {@link com.ticket.identity.MemberLookup}으로 바뀐다.
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = {})
package com.ticket.identity;

import org.springframework.modulith.ApplicationModule;
