package com.ticket.show.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.http.AuthenticatedMemberArgumentResolver;
import com.ticket.shared.exception.handler.GlobalExceptionHandler;
import com.ticket.show.application.usecase.GetMyShowLikesUseCase;
import com.ticket.show.exception.handler.ShowExceptionHandler;

@SuppressWarnings("NonAsciiCharacters")
class MyShowLikesControllerContractTest {
    private static final AuthenticatedMember MEMBER = new AuthenticatedMember(1L, "MEMBER");
    private final GetMyShowLikesUseCase getMyShowLikesUseCase =
            Mockito.mock(GetMyShowLikesUseCase.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MyShowLikesController controller = new MyShowLikesController(getMyShowLikesUseCase);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                        .setControllerAdvice(
                                new GlobalExceptionHandler(), new ShowExceptionHandler())
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(MEMBER, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 옛 {@code ShowLikeCursorCodecTest}가 고정하던 커서 wire 계약을 이어받는다. codec이 controller의 private method가
     * 되면서 HTTP 경계에서만 확인할 수 있다 -- 확인하는 대상(요청 문자열 -> use case 입력, 응답 커서 문자열)은 같다.
     */
    @Test
    void 커서는_마지막_찜_id를_십진수_문자열로_그대로_주고받는다() throws Exception {
        when(getMyShowLikesUseCase.execute(any()))
                .thenReturn(new GetMyShowLikesUseCase.Output(List.of(), false, 9L));

        mockMvc.perform(get("/api/v1/members/me/likes").param("cursor", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value("9"));

        final ArgumentCaptor<GetMyShowLikesUseCase.Input> captor =
                ArgumentCaptor.forClass(GetMyShowLikesUseCase.Input.class);
        verify(getMyShowLikesUseCase).execute(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().cursorLikeId()).isEqualTo(9L);
    }

    @Test
    void 다음_페이지가_없으면_커서를_만들지_않는다() throws Exception {
        when(getMyShowLikesUseCase.execute(any()))
                .thenReturn(new GetMyShowLikesUseCase.Output(List.of(), false, null));

        mockMvc.perform(get("/api/v1/members/me/likes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 커서가_비어있으면_첫_페이지로_본다(final String cursor) throws Exception {
        when(getMyShowLikesUseCase.execute(any()))
                .thenReturn(new GetMyShowLikesUseCase.Output(List.of(), false, null));

        mockMvc.perform(get("/api/v1/members/me/likes").param("cursor", cursor))
                .andExpect(status().isOk());

        final ArgumentCaptor<GetMyShowLikesUseCase.Input> captor =
                ArgumentCaptor.forClass(GetMyShowLikesUseCase.Input.class);
        verify(getMyShowLikesUseCase).execute(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().cursorLikeId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9999999999999999999999"})
    void 커서가_숫자가_아니면_400으로_끊는다(final String cursor) throws Exception {
        mockMvc.perform(get("/api/v1/members/me/likes").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getMyShowLikesUseCase);
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
