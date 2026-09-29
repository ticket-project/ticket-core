package com.ticket.member.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class RegisterMemberRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void supportsLoginIdAlias() throws Exception {
        RegisterMemberRequest request = objectMapper.readValue("""
                        {
                          "loginId": "user@example.com",
                          "password": "password123!",
                          "name": "tester"
                        }
                        """, RegisterMemberRequest.class);

        assertThat(request.getEmail()).isEqualTo("user@example.com");
    }
}
