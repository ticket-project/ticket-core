package com.ticket.core.config.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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

    private final JwtTokenService jwtTokenService;

    public AccessTokenAuthenticationFilter(final JwtTokenService jwtTokenService) {
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService, "jwtTokenService must not be null");
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
            AuthenticatedMember member = jwtTokenService.parse(extractBearerToken(authorizationHeader));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    member,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + member.role()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException exception) {
            deferFailure(request, filterChain, response, "expired", exception);
        } catch (JwtException | IllegalArgumentException exception) {
            deferFailure(request, filterChain, response, "invalid", exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
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
            final String reason,
            final Exception exception
    ) throws ServletException, IOException {
        log.warn("access token verification failed. reason={}", reason, exception);
        request.setAttribute(AUTH_ERROR_ATTRIBUTE, reason);
        filterChain.doFilter(request, response);
    }
}
