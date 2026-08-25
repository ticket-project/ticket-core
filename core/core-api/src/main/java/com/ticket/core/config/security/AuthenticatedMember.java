package com.ticket.core.config.security;

/**
 * 인증된 회원을 나타내는 값이다. 컨트롤러는 이 타입을 파라미터로 받고,
 * SecurityContext에 담긴 주체를 AuthenticatedMemberArgumentResolver가 꺼내 준다.
 */
public record AuthenticatedMember(Long memberId, String role) {

    public AuthenticatedMember {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
