package com.ticket.member.web.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.member.application.RegisterMemberUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterMemberRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsToUseCaseInput() {
        RegisterMemberRequest request = new RegisterMemberRequest("user@example.com", "password123!", "tester");

        RegisterMemberUseCase.Input input = request.toInput();

        assertThat(input.email()).isEqualTo("user@example.com");
        assertThat(input.password()).isEqualTo("password123!");
        assertThat(input.name()).isEqualTo("tester");
    }

    @Test
    void supportsLoginIdAlias() throws Exception {
        RegisterMemberRequest request = objectMapper.readValue(
                """
                        {
                          "loginId": "user@example.com",
                          "password": "password123!",
                          "name": "tester"
                        }
                        """,
                RegisterMemberRequest.class
        );

        assertThat(request.getEmail()).isEqualTo("user@example.com");
    }
}
