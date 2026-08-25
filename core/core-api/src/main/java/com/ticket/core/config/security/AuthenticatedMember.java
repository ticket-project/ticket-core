package com.ticket.core.config.security;

import java.util.Objects;

/**
 * 액세스 토큰에서 꺼낸 인증 주체의 값이다. 토큰 발급·파싱은 이 값만 다루고,
 * Spring Security의 주체 객체 조립은 필터가 맡는다.
 */
public record AuthenticatedMember(Long memberId, String role) {

    public AuthenticatedMember {
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
