package com.ticket.core.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class OAuth2AuthenticationFailureHandlerTest {

    @Test
    void 로컬_프론트에서_시작한_로그인_실패는_로컬_프론트로_리다이렉트한다() throws Exception {
        OAuth2FrontendRedirectResolver resolver = new OAuth2FrontendRedirectResolver(
                "http://localhost:3000",
                "https://oneticket.site",
                "/auth/callback",
                "/auth/callback",
                "https://oneticket.site/auth/callback",
                "https://oneticket.site/auth/callback"
        );
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute(OAuth2FrontendRedirectResolver.SESSION_ATTRIBUTE, "http://localhost:3000");

        handler.onAuthenticationFailure(request, response, new TestAuthenticationException());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/auth/callback?error=oauth2_login_failed");
    }

    private static final class TestAuthenticationException extends AuthenticationException {
        private TestAuthenticationException() {
            super("oauth2 login failed");
        }
    }
}
