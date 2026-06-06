package com.ticket.core.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

@SuppressWarnings("NonAsciiCharacters")
class DeferredPassportFailureHandlerTest {

    private final DeferredPassportFailureHandler handler = new DeferredPassportFailureHandler();

    @Test
    void onFailure는_reason을_jwt_error_속성에_담고_체인을_계속하도록_true를_반환한다() throws Exception {
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        boolean shouldContinue = handler.onFailure(
                request,
                response,
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "expired")
        );

        assertThat(shouldContinue).isTrue();
        assertThat(request.getAttribute("jwt.error")).isEqualTo("expired");
        assertThat(((MockHttpServletResponse) response).getStatus()).isEqualTo(200);
    }
}
