/**
 * Member module: Member, MemberSocialAccount, 이메일·소셜 로그인, OAuth2 provider adapter,
 * 비밀번호, access/refresh token, 회원 상태와 탈퇴, 전역 Spring Security filter chain을 소유한다.
 *
 * <p>구현은 모두 {@code internal} 아래에 있고, 이 module root에는 다른 module이 쓰는 공개 계약만
 * 둔다: {@link com.ticket.member.AuthenticatedMember}(다른 module controller가 parameter로
 * 받는 인증 principal — memberId와 role만 가진다), {@link com.ticket.member.MemberLookup}/
 * {@link com.ticket.member.MemberStatus}(entity 대신 쓰는 회원 조회·활성 검증 계약),
 * {@link com.ticket.member.MemberMetadata}(metadata module이 조합할 Role/SocialProvider
 * code/label).
 *
 * <p>전역 {@code SecurityFilterChain}은 member가 제공한다({@code internal.infrastructure.security.
 * SecurityConfig}) — 인증·인가 해석이 결국 member 소유이기 때문이다.
 *
 * <p>{@code order}·{@code performanceseat}(현재는 booking 소유)는 Task 7에서
 * {@link com.ticket.member.MemberLookup}으로 재배선을 끝냈다. WebSocket 인증도 후속 정리에서
 * 닫혔다 — {@code booking.internal.infrastructure.websocket.WebSocketAuthInterceptor}(booking
 * 소유)는 더 이상 member internal을 직접 참조하지 않고, 이 module이 공개한
 * {@link com.ticket.member.AccessTokenAuthenticator}로만 원본 access token 문자열을 검증한다.
 *
 * <p>찜(showlike)은 더 이상 이 module과 결합이 없다 — {@code ShowLike}·찜 use case·
 * {@code /me/likes} 엔드포인트가 모두 catalog로 옮겨졌고, catalog는 회원 확인만
 * {@link com.ticket.member.MemberLookup}으로 이 module을 참조한다(단방향, 순환 없음).
 */
@ApplicationModule(displayName = "Member", allowedDependencies = {})
package com.ticket.member;

import org.springframework.modulith.ApplicationModule;
