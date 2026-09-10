package com.ticket.shared.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.Optional;

/** SecurityContext의 표준 Principal 이름을 JPA 감사자 ID로 사용한다. */
public class SecurityContextAuditorAware implements AuditorAware<String> {

    private static final String DEFAULT_AUDITOR = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        if (!(authentication.getPrincipal() instanceof Principal principal)) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        return Optional.of(principal.getName());
    }
}
