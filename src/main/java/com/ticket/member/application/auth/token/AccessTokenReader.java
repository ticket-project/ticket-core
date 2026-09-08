package com.ticket.member.application.auth.token;

/**
 * 액세스 토큰에서 인증 주체를 읽는다.
 *
 * <p>토큰을 무엇으로 만들었는지는 어댑터만 안다. 어댑터는 라이브러리 예외를 밖으로 흘리지 않고
 * {@link AccessTokenReadResult}로 중립화해 돌려준다. HTTP 상태 결정은
 * {@code member.infrastructure.security.AccessTokenAuthenticationFilter}가 한다.
 */
public interface AccessTokenReader {

    AccessTokenReadResult read(String accessToken);
}
