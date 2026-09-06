package com.ticket.member.infrastructure.security;

import tools.jackson.databind.json.JsonMapper;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.web.ApiResponse;
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
        final String jwtError = (String) request.getAttribute(JWT_ERROR_ATTRIBUTE);
        final UnauthenticatedException error = new UnauthenticatedException(resolveMessage(jwtError));

        response.setStatus(error.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        jsonMapper.writeValue(response.getWriter(), ApiResponse.error(
                error.getErrorCode().getCode(), error.getMessage(), error.getData()));
    }

    private String resolveMessage(final String jwtError) {
        if (jwtError == null) {
            return UnauthenticatedException.MESSAGE;
        }
        return switch (jwtError) {
            case "expired" -> "토큰이 만료되었습니다. 다시 로그인해주세요.";
            case "invalid" -> "유효하지 않은 토큰입니다.";
            default -> UnauthenticatedException.MESSAGE;
        };
    }
}

