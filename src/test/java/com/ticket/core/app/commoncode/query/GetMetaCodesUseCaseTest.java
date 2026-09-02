package com.ticket.core.app.commoncode.query;

import com.ticket.catalog.internal.domain.show.Category;
import com.ticket.catalog.internal.domain.show.repository.CategoryRepository;
import com.ticket.catalog.internal.domain.show.Genre;
import com.ticket.catalog.internal.domain.show.repository.GenreRepository;
import com.ticket.booking.BookingCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetMetaCodesUseCaseTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private BookingCatalog bookingCatalog;

    @Test
    void 카테고리_장르_enum_코드를_모아_반환한다() {
        Category category = mock(Category.class);
        Genre genre = mock(Genre.class);

        when(category.getId()).thenReturn(1L);
        when(category.getCode()).thenReturn("CONCERT");
        when(category.getName()).thenReturn("콘서트");

        when(genre.getId()).thenReturn(2L);
        when(genre.getCategory()).thenReturn(category);
        when(genre.getCode()).thenReturn("KPOP");
        when(genre.getName()).thenReturn("케이팝");

        when(categoryRepository.findAllOrderById()).thenReturn(List.of(category));
        when(genreRepository.findAllOrderByCategoryAndName()).thenReturn(List.of(genre));
        when(bookingCatalog.performanceSeatStates()).thenReturn(List.of(new BookingCatalog.CodeLabel("AVAILABLE", "예매가능")));
        when(bookingCatalog.holdStates()).thenReturn(List.of(new BookingCatalog.CodeLabel("ACTIVE", "선점 중")));
        when(bookingCatalog.orderStates()).thenReturn(List.of(new BookingCatalog.CodeLabel("PENDING", "결제 대기")));

        GetMetaCodesUseCase useCase = new GetMetaCodesUseCase(categoryRepository, genreRepository, bookingCatalog);

        GetMetaCodesUseCase.Output output = useCase.execute();

        assertThat(output.categories())
                .containsExactly(new GetMetaCodesUseCase.CategoryCodeItem(1L, "CONCERT", "콘서트"));
        assertThat(output.genres())
                .containsExactly(new GetMetaCodesUseCase.GenreCodeItem(2L, "CONCERT", "KPOP", "케이팝"));
        assertThat(output.enums().bookingStatus()).isNotEmpty();
        assertThat(output.enums().showSortKey()).isNotEmpty();
        assertThat(output.enums().performanceSeatState())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("AVAILABLE", "예매가능"));
        assertThat(output.enums().holdState())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("ACTIVE", "선점 중"));
        assertThat(output.enums().orderState())
                .containsExactly(new GetMetaCodesUseCase.EnumCodeItem("PENDING", "결제 대기"));
    }
}
