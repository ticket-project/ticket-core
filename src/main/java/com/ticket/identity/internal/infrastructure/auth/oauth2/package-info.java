/**
 * identity의 카카오 OAuth2 연동 구현(unlink client, 인가 코드 저장소)이다.
 *
 * <p>{@code @NamedInterface("oauth2")}는 {@code com.ticket.config.internal.HttpServiceConfig}가
 * {@link com.ticket.identity.internal.infrastructure.auth.oauth2.KakaoUnlinkApiClient}를
 * {@code @ImportHttpServices}로 등록하기 위해 이 package를 참조할 수 있게 연다({@code com.ticket.config}의
 * package-info 참고). 이 package의 다른 구현({@code KakaoUnlinkHttpClient}, {@code RedisOAuth2AuthCodeStore})도
 * 함께 노출되지만, {@code config}가 실제로 쓰는 건 {@code KakaoUnlinkApiClient} 하나뿐이다 — 이 하나만
 * 더 좁게 떼어내는 재구성은 하지 않았다(과한 조정으로 판단).
 */
@NamedInterface("oauth2")
package com.ticket.identity.internal.infrastructure.auth.oauth2;

import org.springframework.modulith.NamedInterface;
