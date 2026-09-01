package com.ticket.core.infra.persistence;

import com.ticket.core.app.auth.token.AuthenticatedMember;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityContextAuditorAware implements AuditorAware<String> {
    private static final String DEFAULT_AUDITOR = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        if (!(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        return Optional.of(String.valueOf(member.memberId()));
    }
}
