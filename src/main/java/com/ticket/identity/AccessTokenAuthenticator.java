package com.ticket.identity;

/**
 * 다른 module이 원본 access token 문자열로 인증 주체를 얻을 때 쓰는 공개 계약이다.
 *
 * <p>HTTP가 아닌 경로(WebSocket STOMP CONNECT 등)는 Spring Security filter chain을 타지 않아
 * identity의 {@code SecurityContext} 기반 인증을 그대로 쓸 수 없다. 이 계약은 그런 경로를 위해
 * 토큰 검증만 별도로 노출한다 — 만료/무효를 구분해 안내할 필요가 없는 호출부가 대상이므로 둘 다
 * 같은 인증 실패로 다룬다.
 */
public interface AccessTokenAuthenticator {

    /**
     * 원본 access token 문자열을 검증해 인증된 회원을 반환한다. 만료됐거나 무효한 토큰이면
     * identity가 소유한 {@code com.ticket.core.support.exception.CoreException}
     * ({@code ErrorType.AUTHENTICATION_ERROR})을 던진다.
     */
    AuthenticatedMember authenticate(String accessToken);
}
