package com.ticket.core.support.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class CookieUtils {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String SAME_SITE = "None";

    private CookieUtils() {
    }

    public static void addRefreshTokenCookie(final HttpServletResponse response,
                                             final String tokenValue,
                                             final long maxAgeSeconds) {
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
        final ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite(SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
