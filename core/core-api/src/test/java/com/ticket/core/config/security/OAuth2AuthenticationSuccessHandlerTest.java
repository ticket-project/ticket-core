package com.ticket.core.config.security;

import com.ticket.core.domain.auth.oauth2.OAuth2AuthCodeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private OAuth2AuthCodeStore oAuth2AuthCodeStore;

    @Test
    void 로컬_프론트에서_시작한_로그인은_로컬_프론트로_리다이렉트한다() throws Exception {
        OAuth2FrontendRedirectResolver resolver = new OAuth2FrontendRedirectResolver(
                "http://localhost:3000",
                "https://oneticket.site",
                "/auth/callback",
                "/auth/callback",
                "https://oneticket.site/auth/callback",
                "https://oneticket.site/auth/callback"
        );
        OAuth2AuthenticationSuccessHandler handler =
                new OAuth2AuthenticationSuccessHandler(oAuth2AuthCodeStore, resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute(OAuth2FrontendRedirectResolver.SESSION_ATTRIBUTE, "http://localhost:3000");
        MemberPrincipal principal = new MemberPrincipal(7L, "MEMBER");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(oAuth2AuthCodeStore.createCode(7L)).thenReturn("oauth-code");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback?code=oauth-code");
    }
}
