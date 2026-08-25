package com.ticket.core.config.security;

/**
 * 인증된 회원을 나타내는 값이다. 컨트롤러는 이 타입을 파라미터로 받고,
 * AuthenticatedMemberArgumentResolver가 SecurityContext에서 꺼내 준다.
 *
 * <p>프레임워크 계약을 담지 않는다. OAuth2 로그인은 리다이렉트로 끝나 컨트롤러에 닿지 않으므로
 * 그 경로의 주체는 Spring이 제공하는 DefaultOAuth2User가 맡는다. 형제 저장소 ticket-queue의
 * 같은 이름 타입과 형태를 맞춘다.
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
