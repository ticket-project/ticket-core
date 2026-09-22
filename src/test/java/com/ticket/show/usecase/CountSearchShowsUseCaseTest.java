package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CountSearchShowsUseCaseTest {
    @Mock
    private ShowQuerydslRepository showQuerydslRepository;

    @Mock
    private VenueLookupApi venueLookup;

    @InjectMocks
    private CountSearchShowsUseCase useCase;

    @Test
    void 검색_개수를_응답으로_감싼다() {
        ShowSearchCriteria request = new ShowSearchCriteria("뮤지컬", null, null, null, null, null, null);
        when(showQuerydslRepository.countSearchShows(request, null)).thenReturn(42L);

        CountSearchShowsUseCase.Output output = useCase.execute(new CountSearchShowsUseCase.Input(request));

        assertThat(output.count()).isEqualTo(42L);
        verify(showQuerydslRepository).countSearchShows(request, null);
    }

    @Test
    void 검색결과가_없으면_0건을_반환한다() {
        ShowSearchCriteria request = new ShowSearchCriteria("없는공연", null, null, null, null, null, null);
        when(showQuerydslRepository.countSearchShows(request, null)).thenReturn(0L);

        CountSearchShowsUseCase.Output output = useCase.execute(new CountSearchShowsUseCase.Input(request));

        assertThat(output.count()).isZero();
        verify(showQuerydslRepository).countSearchShows(request, null);
    }

    /** 지역 미지정({@code null})과 그 지역에 공연장이 없음(빈 집합)은 다른 조건이다. 뭉개면 "그 지역에 공연장이 없다"가 "전체 목록"으로 조용히 바뀐다. */
    @Test
    void 지역_미지정과_지역_공연장_0건을_구분해_넘긴다() {
        ShowSearchCriteria noRegion = new ShowSearchCriteria(null, null, null, null, null, null, null);
        ShowSearchCriteria jeju = new ShowSearchCriteria(null, null, null, null, null, "JEJU", null);
        when(venueLookup.findIdsByRegion("JEJU")).thenReturn(Set.of());
        when(showQuerydslRepository.countSearchShows(noRegion, null)).thenReturn(7L);
        when(showQuerydslRepository.countSearchShows(jeju, Set.of())).thenReturn(0L);

        assertThat(useCase.execute(new CountSearchShowsUseCase.Input(noRegion)).count())
                .isEqualTo(7L);
        assertThat(useCase.execute(new CountSearchShowsUseCase.Input(jeju)).count())
                .isZero();
    }
}
