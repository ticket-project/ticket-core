package com.ticket.core.api.controller;

import com.ticket.core.config.security.MemberPrincipalArgumentResolver;
import com.ticket.core.app.member.query.GetCurrentMemberUseCase;
import com.ticket.core.app.member.command.WithdrawCurrentMemberUseCase;
import com.ticket.core.app.showlike.query.GetMyShowLikesUseCase;
import com.ticket.core.support.ApiControllerAdvice;
import com.ticket.core.config.security.MemberPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class MemberControllerContractTest {

    private MockMvc mockMvc;

    private final GetCurrentMemberUseCase getCurrentMemberUseCase = Mockito.mock(GetCurrentMemberUseCase.class);

    @BeforeEach
    void setUp() {
        MemberController controller = new MemberController(
                getCurrentMemberUseCase,
                Mockito.mock(WithdrawCurrentMemberUseCase.class),
                Mockito.mock(GetMyShowLikesUseCase.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new MemberPrincipalArgumentResolver())
                .setControllerAdvice(new ApiControllerAdvice())
                .build();
        MemberPrincipal principal = new MemberPrincipal(1L, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of())
        );
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
