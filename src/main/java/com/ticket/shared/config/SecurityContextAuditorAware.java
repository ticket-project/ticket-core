package com.ticket.shared.config;

import com.ticket.shared.AuditorPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** SecurityContext의 기술 중립 감사 주체 식별자를 JPA 감사자 ID로 사용한다. */
public class SecurityContextAuditorAware implements AuditorAware<String> {

    private static final String DEFAULT_AUDITOR = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        if (!(authentication.getPrincipal() instanceof AuditorPrincipal principal)) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        return Optional.of(principal.auditorId());
    }
}
