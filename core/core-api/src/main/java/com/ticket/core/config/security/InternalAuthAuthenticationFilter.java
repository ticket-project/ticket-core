package com.ticket.core.config.security;

import com.ticket.support.passport.Passport;
import com.ticket.support.security.internalauth.InternalAuthPassportService;
import com.ticket.support.security.internalauth.InternalAuthTokenException;
import com.ticket.support.security.internalauth.InternalAuthTokenProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class InternalAuthAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_ERROR_ATTRIBUTE = "jwt.error";

    private final InternalAuthPassportService internalAuthPassportService;

    public InternalAuthAuthenticationFilter(final InternalAuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InternalAuthAuthenticationFilter(final InternalAuthProperties properties, final Clock clock) {
        Objects.requireNonNull(properties, "properties must not be null").validate();
        this.internalAuthPassportService = new InternalAuthPassportService(new InternalAuthTokenProperties(
                properties.getIssuer(),
                properties.getAudience(),
                properties.getSecretKey(),
                properties.getExpirationSeconds()
        ), Objects.requireNonNull(clock, "clock must not be null"));
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        final String internalAuth = request.getHeader(INTERNAL_AUTH_HEADER);

        if (internalAuth != null && internalAuth.startsWith(BEARER_PREFIX)) {
            try {
                final Passport passport = internalAuthPassportService.verifyBearer(internalAuth);
                final UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                passport,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + passport.role()))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InternalAuthTokenException exception) {
                log.warn("내부 인증 토큰 검증에 실패했습니다. message={}", exception.getMessage());
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, resolveError(exception));
            } catch (IllegalArgumentException exception) {
                log.warn("내부 인증 토큰 Passport 변환에 실패했습니다. message={}", exception.getMessage());
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "invalid");
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveError(final InternalAuthTokenException exception) {
        return exception.getMessage() != null && exception.getMessage().contains("expired")
                ? "expired"
                : "invalid";
    }
}
