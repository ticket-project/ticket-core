package com.ticket.security.infrastructure;

import com.ticket.member.AuthenticatedMember;
import com.ticket.member.exception.UnauthenticatedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthenticatedMemberArgumentResolverTest {

    private final AuthenticatedMemberArgumentResolver resolver = new AuthenticatedMemberArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증이_없으면_401을_던진다() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthenticatedException.class);
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
                .isInstanceOf(UnauthenticatedException.class);
    }
}
