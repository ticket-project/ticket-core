package com.ticket.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class LoginRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsToUseCaseInput() {
        LoginRequest request = new LoginRequest("user@example.com", "password123!");

        LoginUseCase.Input input = request.toInput();

        assertThat(input.email()).isEqualTo("user@example.com");
        assertThat(input.password()).isEqualTo("password123!");
    }

    @Test
    void supportsLoginIdAlias() throws Exception {
        LoginRequest request = objectMapper.readValue("""
                        {
                          "id": "user@example.com",
                          "password": "password123!"
                        }
                        """, LoginRequest.class);

        assertThat(request.getEmail()).isEqualTo("user@example.com");
    }
}
