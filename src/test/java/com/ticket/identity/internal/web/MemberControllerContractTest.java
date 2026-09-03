package com.ticket.identity.internal.web;

import com.ticket.identity.internal.infrastructure.security.AuthenticatedMemberArgumentResolver;
import com.ticket.identity.internal.application.member.query.GetCurrentMemberUseCase;
import com.ticket.identity.internal.application.member.command.WithdrawCurrentMemberUseCase;
import com.ticket.core.api.support.cursor.ShowLikeCursorCodec;
import com.ticket.core.app.showlike.query.GetMyShowLikesUseCase;
import com.ticket.error.handler.GlobalExceptionHandler;
import com.ticket.identity.AuthenticatedMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class MemberControllerContractTest {

    private MockMvc mockMvc;

    private final GetCurrentMemberUseCase getCurrentMemberUseCase = Mockito.mock(GetCurrentMemberUseCase.class);
    private final GetMyShowLikesUseCase getMyShowLikesUseCase = Mockito.mock(GetMyShowLikesUseCase.class);

    @BeforeEach
    void setUp() {
        MemberController controller = new MemberController(
                getCurrentMemberUseCase,
                Mockito.mock(WithdrawCurrentMemberUseCase.class),
                getMyShowLikesUseCase,
                new ShowLikeCursorCodec()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        AuthenticatedMember principal = new AuthenticatedMember(1L, "MEMBER");
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

    @Test
    void size가_양수가_아니면_400_계약을_지킨다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/likes").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getMyShowLikesUseCase);
    }
}
