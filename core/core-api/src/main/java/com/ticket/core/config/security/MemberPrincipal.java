package com.ticket.core.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MemberPrincipal implements OAuth2User {
    private final Long memberId;
    private final String role;
    private final Map<String, Object> attributes;
    private final List<GrantedAuthority> authorities;

    public MemberPrincipal(final Long memberId, final String role) {
        this(memberId, role, Map.of());
    }

    public MemberPrincipal(final Long memberId, final String role, final Map<String, Object> attributes) {
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
