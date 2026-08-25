package com.ticket.core.config.security;

import com.ticket.core.app.auth.oauth2.OAuth2AuthCodeStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 성공 시 1회성 auth code를 발급하고 프론트엔드로 리다이렉트합니다.
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2AuthCodeStore oAuth2AuthCodeStore;
    private final OAuth2FrontendRedirectResolver frontendRedirectResolver;

    public OAuth2AuthenticationSuccessHandler(
            final OAuth2AuthCodeStore oAuth2AuthCodeStore,
            final OAuth2FrontendRedirectResolver frontendRedirectResolver
    ) {
        this.oAuth2AuthCodeStore = oAuth2AuthCodeStore;
        this.frontendRedirectResolver = frontendRedirectResolver;
    }

    @Override
    public void onAuthenticationSuccess(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Authentication authentication
    ) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof MemberPrincipal memberPrincipal)) {
            throw new IllegalStateException("Unsupported principal type: " + authentication.getPrincipal().getClass().getName());
        }

        // 1회용 auth code 생성 (Redis, TTL 30초)
        final String authCode = oAuth2AuthCodeStore.createCode(memberPrincipal.getMemberId());

        final String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectResolver.resolveSuccessRedirectUri(request))
                .queryParam("code", authCode)
                .build(true)
                .toUriString();

        frontendRedirectResolver.clear(request);
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
