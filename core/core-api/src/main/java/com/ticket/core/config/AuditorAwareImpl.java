package com.ticket.core.config;

import com.ticket.support.passport.Passport;
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
        if (!(authentication.getPrincipal() instanceof Passport principal)) {
            return Optional.of(DEFAULT_AUDITOR);
        }
        return Optional.of(String.valueOf(principal.memberId()));
    }
}
