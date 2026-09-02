package com.ticket.core.infra.persistence;

import com.ticket.identity.AuthenticatedMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class SecurityContextAuditorAwareTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증된_AuthenticatedMember가_있으면_memberId를_감사자로_반환한다() {
        AuthenticatedMember principal = new AuthenticatedMember(7L, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of())
        );

        SecurityContextAuditorAware auditorAware = new SecurityContextAuditorAware();

        assertThat(auditorAware.getCurrentAuditor()).contains("7");
    }

    @Test
    void 인증정보가_없으면_system_감사자를_반환한다() {
        SecurityContextAuditorAware auditorAware = new SecurityContextAuditorAware();

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void AuthenticatedMember가_아니면_system_감사자를_반환한다() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null)
        );

        SecurityContextAuditorAware auditorAware = new SecurityContextAuditorAware();

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }
}
