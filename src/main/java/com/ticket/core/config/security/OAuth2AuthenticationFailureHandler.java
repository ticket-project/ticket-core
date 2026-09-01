package com.ticket.core.config.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
    private static final String DEFAULT_ERROR_CODE = "oauth2_login_failed";
    private final OAuth2FrontendRedirectResolver frontendRedirectResolver;

    public OAuth2AuthenticationFailureHandler(
            final OAuth2FrontendRedirectResolver frontendRedirectResolver
    ) {
        this.frontendRedirectResolver = frontendRedirectResolver;
    }

    @Override
    public void onAuthenticationFailure(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException exception
    ) throws IOException {
        log.warn("소셜 로그인 OAuth2 인증에 실패했습니다. 사유={}", exception.getMessage(), exception);
        final String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectResolver.resolveFailureRedirectUri(request))
                .queryParam("error", DEFAULT_ERROR_CODE)
                .encode()
                .build()
                .toUriString();

        frontendRedirectResolver.clear(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
