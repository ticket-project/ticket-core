package com.ticket.core.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 인증된 회원을 나타내는 유일한 주체 타입이다. 컨트롤러는 이 타입을 파라미터로 받고,
 * AuthenticatedMemberArgumentResolver가 SecurityContext에서 꺼내 준다.
 *
 * <p>OAuth2UserService가 OAuth2User 반환을 요구하므로 그 계약을 여기에서 함께 만족시킨다.
 * OAuth2 제공자가 준 attributes는 로그인 성공 처리에서 쓰이지 않으므로 담지 않는다.
 */
public record AuthenticatedMember(Long memberId, String role) implements OAuth2User {

    public AuthenticatedMember {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("memberId must be positive");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
