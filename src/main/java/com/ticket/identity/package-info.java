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
 * SecurityConfig}) — 인증·인가 해석이 결국 identity 소유이기 때문이다.
 *
 * <p>{@code order}·{@code performanceseat}(현재는 booking 소유)는 Task 7에서
 * {@link com.ticket.identity.MemberLookup}으로 재배선을 끝냈다. WebSocket 인증도 후속 정리에서
 * 닫혔다 — {@code booking.internal.infrastructure.websocket.WebSocketAuthInterceptor}(booking
 * 소유)는 더 이상 identity internal을 직접 참조하지 않고, 이 module이 공개한
 * {@link com.ticket.identity.AccessTokenAuthenticator}로만 원본 access token 문자열을 검증한다.
 *
 * <p>남은 임시 결합 하나:
 * <ul>
 *   <li>showlike의 legacy 잔존 코드({@code ShowLike}, {@code GetMyShowLikesUseCase},
 *   {@code ShowLikeRepositoryAdapter})가 identity internal {@code Member}를 직접 참조한다 — Task 9가
 *   catalog와의 순환 문제 때문에 의도적으로 legacy에 남겨 뒀다(showlike의 package-info 참고).</li>
 * </ul>
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = {})
package com.ticket.identity;

import org.springframework.modulith.ApplicationModule;
