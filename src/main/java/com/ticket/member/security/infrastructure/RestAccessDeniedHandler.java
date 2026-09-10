package com.ticket.member.security.infrastructure;

import tools.jackson.databind.json.JsonMapper;
import com.ticket.member.support.exception.AuthorizationException;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void handle(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        final AuthorizationException error = new AuthorizationException();

        // MemberExceptionHandler와 같은 상태다 — 이 경로는 filter chain에서 나서 그 handler를
        // 거치지 않으므로 여기서 다시 정한다.
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), ApiResponse.error(
                error.getErrorCode().getCode(), error.getMessage(), error.getData()));
    }
}
