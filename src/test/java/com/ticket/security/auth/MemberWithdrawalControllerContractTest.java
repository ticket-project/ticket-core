package com.ticket.security.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.exception.handler.MemberExceptionHandler;
import com.ticket.security.http.AuthenticatedMemberArgumentResolver;
import com.ticket.shared.exception.handler.GlobalExceptionHandler;

/** 탈퇴 endpoint가 member.web에서 security.auth로 옮겨진 뒤에도 URL·인증 요구·응답이 같은지 고정한다. */
@SuppressWarnings("NonAsciiCharacters")
class MemberWithdrawalControllerContractTest {
    private MockMvc mockMvc;
    private final WithdrawCurrentMemberUseCase withdrawCurrentMemberUseCase =
            Mockito.mock(WithdrawCurrentMemberUseCase.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MemberWithdrawalController(withdrawCurrentMemberUseCase))
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new MemberExceptionHandler())
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(1L, "MEMBER"), null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 탈퇴_API는_기존_URL과_응답_계약을_유지한다() throws Exception {
        when(withdrawCurrentMemberUseCase.execute(any())).thenReturn(new WithdrawCurrentMemberUseCase.Output());

        mockMvc.perform(delete("/api/v1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));

        verify(withdrawCurrentMemberUseCase).execute(new WithdrawCurrentMemberUseCase.Input(1L));
    }

    /** 응답을 내보내기 전에 SecurityContext를 비운다 — 같은 요청 스레드에 인증 주체가 남지 않는다. */
    @Test
    void 탈퇴_후_SecurityContext를_비운다() throws Exception {
        when(withdrawCurrentMemberUseCase.execute(any())).thenReturn(new WithdrawCurrentMemberUseCase.Output());

        mockMvc.perform(delete("/api/v1/members")).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                        SecurityContextHolder.getContext().getAuthentication())
                .isNull();
    }
}
