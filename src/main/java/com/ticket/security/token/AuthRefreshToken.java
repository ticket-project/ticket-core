package com.ticket.security.token;

import com.ticket.security.exception.UnauthenticatedException;

/** 앞뒤 공백을 지운 비어 있지 않은 refresh token이다. 비어 있으면 인증 실패다. */
public record AuthRefreshToken(String value) {
    public AuthRefreshToken {
        value = value == null ? "" : value.trim();
        if (value.isBlank()) {
            throw new UnauthenticatedException();
        }
    }

    public static AuthRefreshToken from(final String value) {
        return new AuthRefreshToken(value);
    }

    /** 토큰 값은 비밀이라 로그에 남기지 않는다. */
    @Override
    public String toString() {
        return "AuthRefreshToken[value=***]";
    }
}
