package com.ticket.like.preference.web;

import com.ticket.like.LikeType;
import com.ticket.like.preference.application.usecase.AddLikeUseCase;
import com.ticket.like.preference.application.usecase.RemoveLikeUseCase;
import com.ticket.like.preference.application.usecase.GetLikeStatusUseCase;
import com.ticket.shared.exception.handler.GlobalExceptionHandler;
import com.ticket.like.exception.handler.LikeExceptionHandler;
import com.ticket.member.AuthenticatedMember;
import com.ticket.member.security.infrastructure.AuthenticatedMemberArgumentResolver;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class LikeControllerContractTest {

    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(100L, "MEMBER");

    private final AddLikeUseCase addLikeUseCase = Mockito.mock(AddLikeUseCase.class);
    private final GetLikeStatusUseCase getLikeStatusUseCase =
            Mockito.mock(GetLikeStatusUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LikeController controller = new LikeController(
                addLikeUseCase,
                Mockito.mock(RemoveLikeUseCase.class),
                getLikeStatusUseCase
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new LikeExceptionHandler())
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
    void 찜_추가는_응답_계약을_지킨다() throws Exception {
        when(addLikeUseCase.execute(new AddLikeUseCase.Input(100L, LikeType.SHOW, 7L)))
                .thenReturn(new AddLikeUseCase.Output(7L, true, 3L));

        mockMvc.perform(post("/api/v1/likes/shows/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.showId").value(7))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(3));
    }

    @Test
    void showId가_양수가_아니면_400_계약을_지킨다() throws Exception {
        mockMvc.perform(post("/api/v1/likes/shows/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(addLikeUseCase);
    }

    @Test
    void 찜_상태_조회도_showId_제약을_지킨다() throws Exception {
        mockMvc.perform(get("/api/v1/likes/shows/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getLikeStatusUseCase);
    }
}
