package com.ticket.identity.internal.infrastructure.security;

import com.ticket.identity.internal.application.auth.token.AccessTokenReadResult;
import com.ticket.identity.AuthenticatedMember;
import com.ticket.identity.internal.application.auth.token.AccessTokenReader;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_ERROR_ATTRIBUTE = "jwt.error";

    private final AccessTokenReader accessTokenReader;

    public AccessTokenAuthenticationFilter(final AccessTokenReader accessTokenReader) {
        this.accessTokenReader = Objects.requireNonNull(accessTokenReader, "accessTokenReader must not be null");
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String token = extractBearerToken(authorizationHeader);
            switch (accessTokenReader.read(token)) {
                case AccessTokenReadResult.Authenticated authenticated -> {
                    authenticate(authenticated.member());
                    filterChain.doFilter(request, response);
                }
                case AccessTokenReadResult.Expired ignored ->
                        deferFailure(request, filterChain, response, "expired");
                case AccessTokenReadResult.Invalid ignored ->
                        deferFailure(request, filterChain, response, "invalid");
            }
        } catch (final IllegalArgumentException exception) {
            // Bearer 형식 자체가 잘못된 경우다. 토큰 검증까지 가지 않는다.
            deferFailure(request, filterChain, response, "invalid");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(final AuthenticatedMember member) {
        final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                member,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.role()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractBearerToken(final String authorizationHeader) {
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("invalid bearer token");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new IllegalArgumentException("invalid bearer token");
        }
        return token;
    }

    private void deferFailure(
            final HttpServletRequest request,
            final FilterChain filterChain,
            final HttpServletResponse response,
            final String reason
    ) throws ServletException, IOException {
        log.warn("access token verification failed. reason={}", reason);
        request.setAttribute(AUTH_ERROR_ATTRIBUTE, reason);
        filterChain.doFilter(request, response);
    }
}
