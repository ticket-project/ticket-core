package com.ticket.show.web;

import com.ticket.show.application.showlike.query.GetMyShowLikesUseCase;
import com.ticket.show.web.support.cursor.ShowLikeCursorCodec;
import com.ticket.error.handler.GlobalExceptionHandler;
import com.ticket.member.AuthenticatedMember;
import com.ticket.member.infrastructure.security.AuthenticatedMemberArgumentResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class MyShowLikesControllerContractTest {

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(1L, "MEMBER");

    private final GetMyShowLikesUseCase getMyShowLikesUseCase = Mockito.mock(GetMyShowLikesUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MyShowLikesController controller = new MyShowLikesController(getMyShowLikesUseCase, new ShowLikeCursorCodec());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MEMBER, null, java.util.List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
