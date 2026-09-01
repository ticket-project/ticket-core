package com.ticket.core.config.security;

import tools.jackson.databind.json.JsonMapper;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String JWT_ERROR_ATTRIBUTE = "jwt.error";
    private final JsonMapper jsonMapper;

    @Override
    public void commence(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException authException
    ) throws IOException, ServletException {
        response.setStatus(ErrorType.AUTHENTICATION_ERROR.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        final String jwtError = (String) request.getAttribute(JWT_ERROR_ATTRIBUTE);
        final String message = resolveMessage(jwtError);
        jsonMapper.writeValue(response.getWriter(), ApiResponse.error(ErrorType.AUTHENTICATION_ERROR, message));
    }

    private String resolveMessage(final String jwtError) {
        if (jwtError == null) {
            return ErrorType.AUTHENTICATION_ERROR.getMessage();
        }
        return switch (jwtError) {
            case "expired" -> "토큰이 만료되었습니다. 다시 로그인해주세요.";
            case "invalid" -> "유효하지 않은 토큰입니다.";
            default -> ErrorType.AUTHENTICATION_ERROR.getMessage();
        };
    }
}

