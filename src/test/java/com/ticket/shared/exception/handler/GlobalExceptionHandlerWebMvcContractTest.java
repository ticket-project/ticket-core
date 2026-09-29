package com.ticket.shared.exception.handler;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ticket.TicketApplication;
import com.ticket.security.http.ApiSecurityConfig;
import com.ticket.security.http.RestAuthenticationEntryPoint;
import com.ticket.security.token.AccessTokenReader;
import com.ticket.show.endpoint.ShowController;
import com.ticket.show.endpoint.cursor.ShowCursorCodec;
import com.ticket.show.usecase.CountSearchShowsUseCase;
import com.ticket.show.usecase.GetLatestShowsUseCase;
import com.ticket.show.usecase.GetMyShowLikesUseCase;
import com.ticket.show.usecase.GetSaleOpeningSoonShowsPageUseCase;
import com.ticket.show.usecase.GetSaleOpeningSoonShowsUseCase;
import com.ticket.show.usecase.GetShowDetailUseCase;
import com.ticket.show.usecase.GetShowsUseCase;
import com.ticket.show.usecase.SearchShowsUseCase;

/**
 * 실제 DispatcherServlet·정적 리소스 handler·API security를 거친 뒤에도 클라이언트 오류가 500이 되지 않는지 고정한다.
 *
 * <p>standalone MockMvc({@link GlobalExceptionHandlerTest})에는 정적 리소스 handler가 없어 없는 경로가
 * {@code NoHandlerFoundException}으로 끝난다. 실제 앱은 {@code /**} resource handler가 마지막에 받아 {@code NoResourceFoundException}을
 * 던지므로, 그 경로는 여기서만 재현된다. 인증이 필요한 경로는 401이 먼저 나가므로 공개 GET 경로 ({@code /api/v1/shows/**})로 확인한다.
 */
@WebMvcTest(controllers = ShowController.class)
@ContextConfiguration(classes = TicketApplication.class)
@Import(ApiSecurityConfig.class)
@TestPropertySource(properties = {"spring.profiles.active=test", "app.cors.allowed-origins=http://localhost:3000"})
@SuppressWarnings("NonAsciiCharacters")
class GlobalExceptionHandlerWebMvcContractTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessTokenReader accessTokenReader;

    @MockitoBean
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @MockitoBean
    private GetShowsUseCase getShowsUseCase;

    @MockitoBean
    private GetLatestShowsUseCase getLatestShowsUseCase;

    @MockitoBean
    private GetSaleOpeningSoonShowsUseCase getSaleOpeningSoonShowsUseCase;

    @MockitoBean
    private GetSaleOpeningSoonShowsPageUseCase getSaleOpeningSoonShowsPageUseCase;

    @MockitoBean
    private SearchShowsUseCase searchShowsUseCase;

    @MockitoBean
    private CountSearchShowsUseCase countSearchShowsUseCase;

    @MockitoBean
    private GetShowDetailUseCase getShowDetailUseCase;

    @MockitoBean
    private GetMyShowLikesUseCase getMyShowLikesUseCase;

    @MockitoBean
    private ShowCursorCodec showCursorCodec;

    @Test
    void 없는_공개_경로는_500이_아니라_404와_E404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/shows/1/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E404"));
    }

    @Test
    void 공연_id가_숫자가_아니면_500이_아니라_400과_E400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/shows/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getShowDetailUseCase);
    }
}
