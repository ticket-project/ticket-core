package com.ticket.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class LoginRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

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
