package com.ticket.core.domain.show.query;

import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.repository.ShowJpaRepository;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ShowFinderTest {

    @Mock
    private ShowJpaRepository showJpaRepository;

    @Mock
    private ShowDetailQueryRepository showDetailQueryRepository;

    @InjectMocks
    private ShowFinder showFinder;

    @Test
    void 공연이_있으면_findById가_그대로_반환한다() {
        Show show = createShow();
        when(showJpaRepository.findById(1L)).thenReturn(Optional.of(show));

        Show result = showFinder.findById(1L);

        assertThat(result).isSameAs(show);
    }

    @Test
    void 공연이_없으면_findById가_not_found_data_예외를_던진다() {
        when(showJpaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> showFinder.findById(1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void 공연이_존재하면_validateShowExists는_예외없이_통과한다() {
        when(showJpaRepository.existsById(1L)).thenReturn(true);

        showFinder.validateShowExists(1L);
    }

    @Test
    void 공연이_없으면_validateShowExists가_not_found_data_예외를_던진다() {
        when(showJpaRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> showFinder.validateShowExists(1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void 공연_상세가_있으면_findShowDetail을_반환한다() {
        GetShowDetailUseCase.Output detail = createShowDetailOutput();
        when(showDetailQueryRepository.findShowDetail(1L)).thenReturn(Optional.of(detail));

        GetShowDetailUseCase.Output result = showFinder.findShowDetail(1L);

        assertThat(result).isSameAs(detail);
    }

    @Test
    void 공연_상세가_없으면_findShowDetail이_not_found_data_예외를_던진다() {
        when(showDetailQueryRepository.findShowDetail(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> showFinder.findShowDetail(1L))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    private Show createShow() {
        return new Show(
                "공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                0L,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 15, 10, 0),
                LocalDateTime.of(2026, 3, 20, 10, 0),
                "image",
                null,
                null,
                120
        );
    }

    private GetShowDetailUseCase.Output createShowDetailOutput() {
        return new GetShowDetailUseCase.Output(
                1L,
                "공연",
                "부제",
                "소개",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                120,
                10L,
                2L,
                null,
                SaleType.GENERAL,
                LocalDateTime.of(2026, 3, 15, 10, 0),
                LocalDateTime.of(2026, 3, 20, 10, 0),
                "image",
                null,
                null,
                List.of("뮤지컬"),
                List.of(),
                List.of()
        );
    }
}
