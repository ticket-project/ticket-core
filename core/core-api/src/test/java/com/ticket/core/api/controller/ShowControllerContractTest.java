package com.ticket.core.api.controller;

import com.ticket.core.domain.performanceseat.query.GetShowSeatsUseCase;
import com.ticket.core.domain.performanceseat.query.GetVenueLayoutUseCase;
import com.ticket.core.domain.performance.query.BookingEntryResolver;
import com.ticket.core.domain.show.BookingStatus;
import com.ticket.core.domain.show.meta.Region;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.query.CountSearchShowsUseCase;
import com.ticket.core.domain.show.query.GetLatestShowsUseCase;
import com.ticket.core.domain.show.query.GetSaleStartApproachingShowsPageUseCase;
import com.ticket.core.domain.show.query.GetSaleStartApproachingShowsUseCase;
import com.ticket.core.domain.show.query.GetShowDetailUseCase;
import com.ticket.core.domain.show.query.GetShowsUseCase;
import com.ticket.core.domain.show.query.SearchShowsUseCase;
import com.ticket.core.domain.show.query.model.ShowListItemView;
import com.ticket.core.domain.show.query.model.ShowSearchItemView;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("NonAsciiCharacters")
class ShowControllerContractTest {

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
                mock(GetVenueLayoutUseCase.class)
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
                .thenReturn(new GetShowsUseCase.Output(new SliceImpl<>(List.of(show)), "next"));

        mockMvc.perform(get("/api/v1/shows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.numberOfElements").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value("next"))
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
                mock(GetVenueLayoutUseCase.class)
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
                .thenReturn(new SearchShowsUseCase.Output(new SliceImpl<>(List.of(item)), "cursor-1"));

        mockMvc.perform(get("/api/v1/shows/search").param("keyword", "공연"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value("cursor-1"))
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
                mock(GetVenueLayoutUseCase.class)
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
        GetShowDetailUseCase.Output detail = new GetShowDetailUseCase.Output(
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
}
