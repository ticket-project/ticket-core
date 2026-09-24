package com.ticket.member.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ticket.member.exception.DuplicateEmailException;
import com.ticket.member.exception.handler.MemberExceptionHandler;
import com.ticket.member.usecase.RegisterMemberUseCase;
import com.ticket.shared.exception.handler.GlobalExceptionHandler;

@SuppressWarnings("NonAsciiCharacters")
class MemberRegistrationControllerContractTest {
    private final RegisterMemberUseCase registerMemberUseCase = Mockito.mock(RegisterMemberUseCase.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberRegistrationController(registerMemberUseCase))
                .setControllerAdvice(new GlobalExceptionHandler(), new MemberExceptionHandler())
                .build();
    }

    @Test
    void 회원가입_경로와_응답_형식을_유지한다() throws Exception {
        when(registerMemberUseCase.execute(new RegisterMemberUseCase.Input("user@example.com", "password123!", "홍길동")))
                .thenReturn(new RegisterMemberUseCase.Output(11L));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123!","name":"홍길동"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.memberId").value(11))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void 중복_이메일은_기존_409_형식을_유지한다() throws Exception {
        when(registerMemberUseCase.execute(any(RegisterMemberUseCase.Input.class)))
                .thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"password123!","name":"홍길동"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("E2000"));
    }
}
