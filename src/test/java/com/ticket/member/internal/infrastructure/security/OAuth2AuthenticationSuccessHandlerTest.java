package com.ticket.member.internal.infrastructure.security;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import java.util.Map;
import java.util.List;
import com.ticket.member.internal.application.auth.oauth2.IssueOAuth2AuthCodeUseCase;
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
    private IssueOAuth2AuthCodeUseCase issueAuthCodeUseCase;

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
                new OAuth2AuthenticationSuccessHandler(issueAuthCodeUseCase, resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession(true).setAttribute(OAuth2FrontendRedirectResolver.SESSION_ATTRIBUTE, "http://localhost:3000");
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
        OAuth2User principal = new DefaultOAuth2User(authorities, Map.of("memberId", 7L), "memberId");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        when(issueAuthCodeUseCase.execute(7L)).thenReturn("oauth-code");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback?code=oauth-code");
    }
}
