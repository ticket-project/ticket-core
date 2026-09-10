package com.ticket.member;

import java.security.Principal;

/**
 * 인증된 회원을 나타내는 공개 계약이다. memberId와 인가에 꼭 필요한 role만 가진 불변 값으로,
 * JWT나 JPA {@code Member} entity를 다른 module에 노출하지 않는다. 다른 module의 controller는
 * 이 타입만 parameter로 받고, {@code AuthenticatedMemberArgumentResolver}가 SecurityContext에서
 * 꺼내 준다.
 *
 * <p>Spring/JPA 타입은 공개 계약에 담지 않는다. 다만 JPA 감사가 표준 주체 이름을 읽을 수 있도록
 * {@link Principal}을 구현하고, 이름은 회원 ID 문자열로 제공한다. OAuth2 로그인은 리다이렉트로 끝나 컨트롤러에 닿지 않으므로
 * 그 경로의 주체는 Spring이 제공하는 DefaultOAuth2User가 맡는다. 형제 저장소 ticket-queue의
 * 같은 이름 타입과 형태를 맞춘다.
 */
public record AuthenticatedMember(Long memberId, String role) implements Principal {

    public AuthenticatedMember {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
