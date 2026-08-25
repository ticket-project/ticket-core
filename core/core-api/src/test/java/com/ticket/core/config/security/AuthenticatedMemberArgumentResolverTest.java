package com.ticket.core.config.security;

import com.ticket.core.app.auth.token.AuthenticatedMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class AuthenticatedMemberArgumentResolverTest {

    private final AuthenticatedMemberArgumentResolver resolver = new AuthenticatedMemberArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증이_없으면_401을_던진다() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void 인증된_AuthenticatedMember를_반환한다() {
        AuthenticatedMember principal = new AuthenticatedMember(10L, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );

        Object resolved = resolver.resolveArgument(null, null, null, null);

        assertThat(resolved).isSameAs(principal);
    }

    @Test
    void principal이_AuthenticatedMember가_아니면_401을_던진다() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("member", null, List.of())
        );

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
