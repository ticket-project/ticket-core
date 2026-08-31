package com.ticket.core.api.controller;

import com.ticket.core.api.error.GlobalExceptionHandler;
import com.ticket.core.app.performanceseat.query.GetShowSeatsUseCase;
import com.ticket.core.app.performanceseat.query.GetVenueLayoutUseCase;
import com.ticket.core.domain.performance.policy.BookingEntryResolver;
import com.ticket.core.domain.show.model.BookingStatus;
import com.ticket.core.domain.show.model.Region;
import com.ticket.core.domain.show.model.SaleType;
import com.ticket.core.app.show.query.CountSearchShowsUseCase;
import com.ticket.core.app.show.query.GetLatestShowsUseCase;
import com.ticket.core.app.show.query.GetSaleStartApproachingShowsPageUseCase;
import com.ticket.core.app.show.query.GetSaleStartApproachingShowsUseCase;
import com.ticket.core.app.show.query.GetShowDetailUseCase;
import com.ticket.core.app.show.query.GetShowsUseCase;
import com.ticket.core.app.show.query.SearchShowsUseCase;
import com.ticket.core.app.show.query.model.ShowListItemView;
import com.ticket.core.app.show.query.model.ShowSearchItemView;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.ticket.core.api.support.cursor.ShowCursorCodec;
import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.core.app.show.query.ShowSort;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("NonAsciiCharacters")
class ShowControllerContractTest {

    private static final ShowCursor NEXT_POSITION =
            new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    /** 기존 wire 포맷을 유지한다: URL-safe Base64(JSON(ShowCursor)). */
    private static final String EXPECTED_NEXT_CURSOR = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(
                    JsonMapper.builder().build().writeValueAsString(NEXT_POSITION)
                            .getBytes(StandardCharsets.UTF_8));


    @Test
    void 공연_목록_api는_슬라이스_응답_계약을_유지한다() throws Exception {
        GetShowsUseCase getShowsUseCase = mock(GetShowsUseCase.class);
        ShowController controller = new ShowController(
                getShowsUseCase,
                mock(GetLatestShowsUseCase.class),
                mock(GetSaleStartApproachingShowsUseCase.class),
                mock(GetSaleStartApproachingShowsPageUseCase.class),
                mock(SearchShowsUseCase.class),
                mock(CountSearchShowsUseCase.class),
                mock(GetShowDetailUseCase.class),
                mock(GetShowSeatsUseCase.class),
                mock(GetVenueLayoutUseCase.class),
                new ShowCursorCodec(JsonMapper.builder().build())
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        ShowListItemView show = new ShowListItemView(
                1L,
                "공연",
                "부제",
                "image",
                List.of("장르"),
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 21),
                10L,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 19, 10, 0),
                LocalDateTime.of(2026, 3, 21, 10, 0),
                LocalDateTime.of(2026, 3, 18, 10, 0),
                Region.SEOUL,
                "장소"
        );
        when(getShowsUseCase.execute(any(GetShowsUseCase.Input.class)))
                .thenReturn(new GetShowsUseCase.Output(List.of(show), true, NEXT_POSITION));

        mockMvc.perform(get("/api/v1/shows").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.numberOfElements").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value(EXPECTED_NEXT_CURSOR))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void 공연_검색_api는_슬라이스_응답_계약을_유지한다() throws Exception {
        SearchShowsUseCase searchShowsUseCase = mock(SearchShowsUseCase.class);
        ShowController controller = new ShowController(
                mock(GetShowsUseCase.class),
                mock(GetLatestShowsUseCase.class),
                mock(GetSaleStartApproachingShowsUseCase.class),
                mock(GetSaleStartApproachingShowsPageUseCase.class),
                searchShowsUseCase,
                mock(CountSearchShowsUseCase.class),
                mock(GetShowDetailUseCase.class),
                mock(GetShowSeatsUseCase.class),
                mock(GetVenueLayoutUseCase.class),
                new ShowCursorCodec(JsonMapper.builder().build())
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        ShowSearchItemView item = new ShowSearchItemView(
                1L,
                "공연",
                "image",
                "장소",
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 21),
                Region.SEOUL,
                10L
        );
        when(searchShowsUseCase.execute(any(SearchShowsUseCase.Input.class)))
                .thenReturn(new SearchShowsUseCase.Output(List.of(item), true, NEXT_POSITION));

        mockMvc.perform(get("/api/v1/shows/search").param("keyword", "공연"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value(EXPECTED_NEXT_CURSOR))
                .andExpect(jsonPath("$.error").isEmpty());
    }
    @Test
    void show_detail_response_includes_booking_entry_for_each_performance() throws Exception {
        GetShowDetailUseCase getShowDetailUseCase = mock(GetShowDetailUseCase.class);
        ShowController controller = new ShowController(
                mock(GetShowsUseCase.class),
                mock(GetLatestShowsUseCase.class),
                mock(GetSaleStartApproachingShowsUseCase.class),
                mock(GetSaleStartApproachingShowsPageUseCase.class),
                mock(SearchShowsUseCase.class),
                mock(CountSearchShowsUseCase.class),
                getShowDetailUseCase,
                mock(GetShowSeatsUseCase.class),
                mock(GetVenueLayoutUseCase.class),
                new ShowCursorCodec(JsonMapper.builder().build())
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        GetShowDetailUseCase.PerformanceInfo performance = new GetShowDetailUseCase.PerformanceInfo(
                10L,
                1L,
                LocalDateTime.of(2026, 3, 20, 19, 0),
                LocalDateTime.of(2026, 3, 20, 21, 0),
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 20, 20, 0),
                BookingEntryResolver.EntryType.QUEUE,
                true,
                null,
                "/api/v1/queue/performances/10/enter"
        );
        GetShowDetailUseCase.Output detail = new ShowDetailView(
                1L,
                "공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 21),
                120,
                10L,
                2L,
                BookingStatus.ON_SALE,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 10, 10, 0),
                LocalDateTime.of(2026, 3, 20, 20, 0),
                "image",
                null,
                null,
                List.of("콘서트"),
                List.of(),
                List.of(new GetShowDetailUseCase.PerformanceDateInfo(
                        LocalDate.of(2026, 3, 20),
                        List.of(performance)
                ))
        );

        when(getShowDetailUseCase.execute(new GetShowDetailUseCase.Input(1L))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/shows/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceDates[0].performances[0].entryType").value("QUEUE"))
                .andExpect(jsonPath("$.data.performanceDates[0].performances[0].queueRequired").value(true))
                .andExpect(jsonPath("$.data.performanceDates[0].performances[0].queueEnterUrl")
                        .value("/api/v1/queue/performances/10/enter"));
    }

    @Test
    void showId가_양수가_아니면_400_계약을_지킨다() throws Exception {
        GetShowDetailUseCase getShowDetailUseCase = mock(GetShowDetailUseCase.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(newController(getShowDetailUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/shows/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getShowDetailUseCase);
    }

    @Test
    void size가_양수가_아니면_400_계약을_지킨다() throws Exception {
        GetShowsUseCase getShowsUseCase = mock(GetShowsUseCase.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(newControllerWithShows(getShowsUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/shows").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.error.code").value("E400"));

        verifyNoInteractions(getShowsUseCase);
    }

    private ShowController newController(final GetShowDetailUseCase getShowDetailUseCase) {
        return new ShowController(
                mock(GetShowsUseCase.class),
                mock(GetLatestShowsUseCase.class),
                mock(GetSaleStartApproachingShowsUseCase.class),
                mock(GetSaleStartApproachingShowsPageUseCase.class),
                mock(SearchShowsUseCase.class),
                mock(CountSearchShowsUseCase.class),
                getShowDetailUseCase,
                mock(GetShowSeatsUseCase.class),
                mock(GetVenueLayoutUseCase.class),
                new ShowCursorCodec(JsonMapper.builder().build())
        );
    }

    private ShowController newControllerWithShows(final GetShowsUseCase getShowsUseCase) {
        return new ShowController(
                getShowsUseCase,
                mock(GetLatestShowsUseCase.class),
                mock(GetSaleStartApproachingShowsUseCase.class),
                mock(GetSaleStartApproachingShowsPageUseCase.class),
                mock(SearchShowsUseCase.class),
                mock(CountSearchShowsUseCase.class),
                mock(GetShowDetailUseCase.class),
                mock(GetShowSeatsUseCase.class),
                mock(GetVenueLayoutUseCase.class),
                new ShowCursorCodec(JsonMapper.builder().build())
        );
    }
}
