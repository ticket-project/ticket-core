package com.ticket.member;

import com.ticket.shared.AuditorPrincipal;

/**
 * 인증된 회원을 나타내는 공개 계약이다. memberId와 인가에 꼭 필요한 role만 가진 불변 값으로,
 * JWT나 JPA {@code Member} entity를 다른 module에 노출하지 않는다. 다른 module의 controller는
 * 이 타입만 parameter로 받고, {@code AuthenticatedMemberArgumentResolver}가 SecurityContext에서
 * 꺼내 준다.
 *
 * <p>Spring/JPA 타입은 공개 계약에 담지 않는다. JPA 감사에는 shared의 기술 중립
 * {@link AuditorPrincipal} 계약으로 회원 ID 문자열을 제공한다. OAuth2 로그인은 리다이렉트로 끝나
 * 컨트롤러에 닿지 않으므로 그 경로의 주체는 Spring이 제공하는 DefaultOAuth2User가 맡는다.
 * 형제 저장소 ticket-queue의 같은 이름 타입과 데이터 형태를 맞춘다.
 */
public record AuthenticatedMember(Long memberId, String role) implements AuditorPrincipal {

    public AuthenticatedMember {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }

    @Override
    public String auditorId() {
        return String.valueOf(memberId);
    }
}
