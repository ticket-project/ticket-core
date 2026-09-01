package com.ticket.core.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class OAuth2FrontendRedirectCaptureFilter extends OncePerRequestFilter {

    private final OAuth2FrontendRedirectResolver frontendRedirectResolver;

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getRequestURI().startsWith(OAuth2EndpointConstants.AUTHORIZATION_BASE_URI)) {
            frontendRedirectResolver.storeFrontendBaseUrl(request);
        }
        filterChain.doFilter(request, response);
    }
}
