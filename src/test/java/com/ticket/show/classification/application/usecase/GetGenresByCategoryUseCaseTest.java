package com.ticket.show.classification.application.usecase;

import com.ticket.show.classification.domain.Genre;
import com.ticket.show.classification.domain.GenreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class GetGenresByCategoryUseCaseTest {

    @Mock
    private GenreRepository genreRepository;
    @InjectMocks
    private GetGenresByCategoryUseCase useCase;

    @Test
    void 카테고리코드가_비어있으면_전체_장르를_조회한다() {
        //given
        Genre genre = createGenre(1L, "KPOP", "케이팝");
        when(genreRepository.findAllOrderByCategoryAndName()).thenReturn(List.of(genre));

        //when
        GetGenresByCategoryUseCase.Output output = useCase.execute(new GetGenresByCategoryUseCase.Input(" "));

        //then
        assertThat(output.genres()).extracting("code").containsExactly("KPOP");
        verify(genreRepository).findAllOrderByCategoryAndName();
    }

    @Test
    void 카테고리코드가_null이면_전체_장르를_조회한다() {
        //given
        Genre genre = createGenre(1L, "KPOP", "케이팝");
        when(genreRepository.findAllOrderByCategoryAndName()).thenReturn(List.of(genre));

        //when
        GetGenresByCategoryUseCase.Output output = useCase.execute(new GetGenresByCategoryUseCase.Input(null));

        //then
        assertThat(output.genres()).extracting("code").containsExactly("KPOP");
        verify(genreRepository).findAllOrderByCategoryAndName();
    }

    @Test
    void 카테고리코드가_있으면_해당_장르만_조회한다() {
        //given
        Genre genre = createGenre(1L, "KPOP", "케이팝");
        when(genreRepository.findAllByCategoryCodeOrderByName("CONCERT")).thenReturn(List.of(genre));

        //when
        GetGenresByCategoryUseCase.Output output = useCase.execute(new GetGenresByCategoryUseCase.Input("CONCERT"));

        //then
        assertThat(output.genres()).hasSize(1);
        verify(genreRepository).findAllByCategoryCodeOrderByName("CONCERT");
    }

    private Genre createGenre(final Long id, final String code, final String name) {
        Genre genre = mock(Genre.class);
        when(genre.getId()).thenReturn(id);
        when(genre.getCode()).thenReturn(code);
        when(genre.getName()).thenReturn(name);
        return genre;
    }
}
