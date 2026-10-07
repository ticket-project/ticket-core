package com.ticket.security.http;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class RefreshTokenCookieWriter {
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE = "None";

    private RefreshTokenCookieWriter() {}

    /** 쿠키 만료는 토큰을 발급한 쪽이 정한 값을 그대로 쓴다. 설정을 다시 읽으면 저장소 TTL과 어긋날 수 있다. */
    public static void addRefreshTokenCookie(
            final HttpServletResponse response, final String tokenValue, final long maxAgeSeconds) {
        final ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void deleteRefreshTokenCookie(final HttpServletResponse response) {
        addRefreshTokenCookie(response, "", 0);
    }
}
