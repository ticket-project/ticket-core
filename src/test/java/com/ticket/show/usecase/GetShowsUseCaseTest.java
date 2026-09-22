package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.shared.api.CursorPage;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowsUseCaseTest {
    private static final ShowCursor NEXT_POSITION = new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

    @Mock
    private ShowQuerydslRepository showQuerydslRepository;

    @Mock
    private VenueLookupApi venueLookup;

    @Spy
    private ShowCardImagePathConverter showCardImagePathConverter = new ShowCardImagePathConverter();

    @InjectMocks
    private GetShowsUseCase useCase;

    @Test
    void 공연_목록과_커서를_반환한다() {
        ShowListParam param = new ShowListParam(null, null, null, null);
        LocalDate startDate = LocalDate.of(2026, 3, 27);
        LocalDate endDate = LocalDate.of(2026, 3, 28);
        LocalDateTime saleStartDate = LocalDateTime.of(2026, 3, 20, 10, 0);
        LocalDateTime saleEndDate = LocalDateTime.of(2026, 3, 28, 10, 0);
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 1, 10, 0);

        Show show = ShowFixture.show(
                1L, "concert", "subtitle", "image", 7L, startDate, endDate, saleStartDate, saleEndDate, 10L, createdAt);
        CursorPage<Show, ShowCursor> result = new CursorPage<>(List.of(show), true, NEXT_POSITION);
        when(showQuerydslRepository.findAllBySearch(param, null, 10, ShowSort.POPULAR))
                .thenReturn(result);
        when(showQuerydslRepository.findGenreNamesByShowIds(List.of(1L))).thenReturn(Map.of(1L, List.of("rock")));
        when(venueLookup.getSummaries(Set.of(7L)))
                .thenReturn(Map.of(
                        7L,
                        new VenueSnapshot(
                                7L,
                                "venue",
                                "주소",
                                null,
                                null,
                                null,
                                null,
                                null,
                                new VenueSnapshot.SeatMapLayout(0, 0, 0.0))));

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.items()).hasSize(1);
        assertThat(output.items().getFirst().title()).isEqualTo("concert");
        assertThat(output.items().getFirst().genreNames()).containsExactly("rock");
        assertThat(output.items().getFirst().venue()).isEqualTo("venue");
        assertThat(output.nextPosition()).isEqualTo(NEXT_POSITION);
        assertThat(output.hasNext()).isTrue();
        verify(showQuerydslRepository).findAllBySearch(param, null, 10, ShowSort.POPULAR);
    }

    @Test
    void 공연이_없으면_빈_슬라이스와_null_커서를_반환한다() {
        ShowListParam param = new ShowListParam(null, null, null, null);
        CursorPage<Show, ShowCursor> result = new CursorPage<>(List.of(), false, null);
        when(showQuerydslRepository.findAllBySearch(param, null, 10, ShowSort.POPULAR))
                .thenReturn(result);

        GetShowsUseCase.Output output = useCase.execute(new GetShowsUseCase.Input(param, 10, ShowSort.from("popular")));

        assertThat(output.items()).isEmpty();
        assertThat(output.nextPosition()).isNull();
        assertThat(output.hasNext()).isFalse();
        verify(showQuerydslRepository).findAllBySearch(param, null, 10, ShowSort.POPULAR);
    }

    /** 지역 미지정({@code null})과 그 지역에 공연장이 없음(빈 집합)은 다른 조건이다. 뭉개면 "그 지역에 공연장이 없다"가 "전체 목록"으로 조용히 바뀐다. */
    @Test
    void 지역_미지정과_지역_공연장_0건을_구분해_넘긴다() {
        CursorPage<Show, ShowCursor> empty = new CursorPage<>(List.of(), false, null);
        ShowListParam noRegion = new ShowListParam(null, null, null, null);
        ShowListParam jeju = new ShowListParam(null, null, Region.JEJU, null);
        when(venueLookup.findIdsByRegion(Region.JEJU)).thenReturn(Set.of());
        when(showQuerydslRepository.findAllBySearch(noRegion, null, 10, ShowSort.POPULAR))
                .thenReturn(empty);
        when(showQuerydslRepository.findAllBySearch(jeju, Set.of(), 10, ShowSort.POPULAR))
                .thenReturn(empty);

        useCase.execute(new GetShowsUseCase.Input(noRegion, 10, ShowSort.POPULAR));
        useCase.execute(new GetShowsUseCase.Input(jeju, 10, ShowSort.POPULAR));

        verify(showQuerydslRepository).findAllBySearch(noRegion, null, 10, ShowSort.POPULAR);
        verify(showQuerydslRepository).findAllBySearch(jeju, Set.of(), 10, ShowSort.POPULAR);
    }
}
