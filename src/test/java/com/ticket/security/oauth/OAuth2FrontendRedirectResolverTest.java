package com.ticket.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@SuppressWarnings("NonAsciiCharacters")
class OAuth2FrontendRedirectResolverTest {
    private final OAuth2FrontendRedirectResolver resolver = new OAuth2FrontendRedirectResolver(
            "http://localhost:3000",
            "https://oneticket.site",
            "/auth/callback",
            "/auth/callback",
            "https://oneticket.site/auth/callback",
            "https://oneticket.site/auth/callback");

    @Test
    void 로컬_프론트_origin이면_로컬_프론트_base_url을_고른다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://localhost:3000");

        assertThat(resolver.resolveFrontendBaseUrl(request)).isEqualTo("http://localhost:3000");
    }

    @Test
    void 운영_도메인_referer면_운영_프론트_base_url을_고른다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", "https://oneticket.site/login");

        assertThat(resolver.resolveFrontendBaseUrl(request)).isEqualTo("https://oneticket.site");
    }

    @Test
    void origin과_referer가_없으면_프론트_base_url을_고르지_않는다() {
        assertThat(resolver.resolveFrontendBaseUrl(new MockHttpServletRequest()))
                .isNull();
    }

    @Test
    void 저장된_프론트_base_url이_있으면_성공_리다이렉트는_그_base_url과_path를_사용한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        resolver.rememberFrontendBaseUrl(request, "http://localhost:3000");

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
