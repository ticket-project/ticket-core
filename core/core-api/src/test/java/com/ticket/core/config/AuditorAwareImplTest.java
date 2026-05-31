package com.ticket.core.config;

import com.ticket.support.passport.Passport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class AuditorAwareImplTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증된_Passport가_있으면_memberId를_감사자로_반환한다() {
        Passport principal = new Passport(7L, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of())
        );

        AuditorAwareImpl auditorAware = new AuditorAwareImpl();

        assertThat(auditorAware.getCurrentAuditor()).contains("7");
    }

    @Test
    void 인증정보가_없으면_system_감사자를_반환한다() {
        AuditorAwareImpl auditorAware = new AuditorAwareImpl();

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void Passport가_아니면_system_감사자를_반환한다() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
        );

        AuditorAwareImpl auditorAware = new AuditorAwareImpl();

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }
}
