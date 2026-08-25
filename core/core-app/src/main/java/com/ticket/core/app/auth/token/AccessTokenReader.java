package com.ticket.core.app.auth.token;

/**
 * 액세스 토큰에서 인증 주체를 읽는다. 토큰을 무엇으로 만들었는지는 어댑터만 알고,
 * 인증 필터는 이 포트만 본다.
 */
public interface AccessTokenReader {

    /**
     * @throws RuntimeException 토큰이 만료됐거나 형식이 올바르지 않은 경우.
     *                          예외 종류는 어댑터가 정하며 필터가 그대로 분류한다.
     */
    AuthenticatedMember read(String accessToken);
}
