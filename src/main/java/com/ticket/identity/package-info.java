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
 * {@link com.ticket.identity.MemberLookup}으로 재배선을 끝냈다. 남은 임시 결합 둘:
 * <ul>
 *   <li>{@code com.ticket.core.config.security.WebSocketAuthInterceptor}가 identity가 internal로
 *   옮긴 {@code AccessTokenReader}/{@code AccessTokenReadResult}를 직접 참조한다. booking module은
 *   이미 있지만(Task 7) WebSocket 설정 자체는 아직 legacy에서 옮겨지지 않았다 — 차단 요인은 없고
 *   단지 아직 하지 않은 상태다.</li>
 *   <li>showlike의 legacy 잔존 코드({@code ShowLike}, {@code GetMyShowLikesUseCase},
 *   {@code ShowLikeRepositoryAdapter})가 identity internal {@code Member}를 직접 참조한다 — Task 9가
 *   catalog와의 순환 문제 때문에 의도적으로 legacy에 남겨 뒀다(showlike의 package-info 참고).</li>
 * </ul>
 */
@ApplicationModule(displayName = "Identity", allowedDependencies = {})
package com.ticket.identity;

import org.springframework.modulith.ApplicationModule;
