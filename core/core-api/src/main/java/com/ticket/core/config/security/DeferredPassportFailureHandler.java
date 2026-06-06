package com.ticket.core.config.security;

import com.ticket.support.passport.web.PassportFailureHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ResponseStatusException;

/**
 * Records the passport verification failure as a request attribute and lets the request continue,
 * so Spring Security's authorization rules and {@link RestAuthenticationEntryPoint} decide the response.
 *
 * <p>This preserves core's behavior where a public endpoint stays accessible even with a stale token,
 * and a protected endpoint returns a 401 whose message distinguishes {@code expired} from {@code invalid}.
 */
@Slf4j
public class DeferredPassportFailureHandler implements PassportFailureHandler {

    private static final String AUTH_ERROR_ATTRIBUTE = "jwt.error";

    @Override
    public boolean onFailure(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final ResponseStatusException exception
    ) {
        log.warn("내부 인증 토큰 검증에 실패했습니다. reason={}", exception.getReason());
        request.setAttribute(AUTH_ERROR_ATTRIBUTE, exception.getReason());
        return true;
    }
}
