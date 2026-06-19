package com.ticket.core.config;

import com.ticket.core.config.security.MemberPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<String> {
    private static final String DEFAULT_AUDITOR = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        if (!(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        return Optional.of(String.valueOf(principal.getMemberId()));
    }
}
