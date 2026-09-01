package com.ticket.core.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class OAuth2FrontendRedirectResolverTest {

    private final OAuth2FrontendRedirectResolver resolver = new OAuth2FrontendRedirectResolver(
            "http://localhost:3000",
            "https://oneticket.site",
            "/auth/callback",
            "/auth/callback",
            "https://oneticket.site/auth/callback",
            "https://oneticket.site/auth/callback"
    );

    @Test
    void 로컬_프론트_origin이면_세션에_로컬_프론트_base_url을_저장한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://localhost:3000");

        resolver.storeFrontendBaseUrl(request);

        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(OAuth2FrontendRedirectResolver.SESSION_ATTRIBUTE))
                .isEqualTo("http://localhost:3000");
    }

    @Test
    void 저장된_프론트_base_url이_있으면_성공_리다이렉트는_그_base_url과_path를_사용한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(OAuth2FrontendRedirectResolver.SESSION_ATTRIBUTE, "http://localhost:3000");

        String result = resolver.resolveSuccessRedirectUri(request);

        assertThat(result).isEqualTo("http://localhost:3000/auth/callback");
    }

    @Test
    void 저장된_프론트_base_url이_없으면_기본_성공_리다이렉트_uri를_사용한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String result = resolver.resolveSuccessRedirectUri(request);

        assertThat(result).isEqualTo("https://oneticket.site/auth/callback");
    }
}
