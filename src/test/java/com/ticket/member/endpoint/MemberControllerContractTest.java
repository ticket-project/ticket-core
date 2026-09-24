package com.ticket.member.endpoint;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ticket.member.usecase.GetCurrentMemberUseCase;
import com.ticket.security.exception.handler.SecurityExceptionHandler;
import com.ticket.security.http.AuthenticatedMemberArgumentResolver;
import com.ticket.shared.exception.handler.GlobalExceptionHandler;

@SuppressWarnings("NonAsciiCharacters")
class MemberControllerContractTest {
    private MockMvc mockMvc;
    private final GetCurrentMemberUseCase getCurrentMemberUseCase = Mockito.mock(GetCurrentMemberUseCase.class);

    @BeforeEach
    void setUp() {
        MemberController controller = new MemberController(getCurrentMemberUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(
                        new GlobalExceptionHandler(), new MemberExceptionHandler(), new SecurityExceptionHandler())
                .build();
        AuthenticatedMember principal = new AuthenticatedMember(1L, "MEMBER");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 내_정보_API는_응답_계약을_유지한다() throws Exception {
        when(getCurrentMemberUseCase.execute(new GetCurrentMemberUseCase.Input(1L)))
                .thenReturn(new GetCurrentMemberUseCase.Output(1L, "user@example.com", "홍길동", "MEMBER"));

        mockMvc.perform(get("/api/v1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.memberId").value(1))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.error").isEmpty());
    }
}
