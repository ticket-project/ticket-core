package com.ticket.core.app.show.query;

import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.BookingStatus;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetShowDetailUseCaseTest {

    @Mock
    private ShowDetailQueryRepository showDetailQueryRepository;

    @InjectMocks
    private GetShowDetailUseCase useCase;

    @Test
    void 공연_상세를_그대로_반환한다() {
        GetShowDetailUseCase.Output detail = new GetShowDetailUseCase.Output(
                1L,
                "공연",
                "부제",
                "info",
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                120,
                100L,
                10L,
                BookingStatus.ON_SALE,
                SaleType.GENERAL,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(1),
                "image",
                null,
                null,
                List.of("장르"),
                List.of(),
                List.of()
        );
        when(showDetailQueryRepository.findShowDetail(1L)).thenReturn(Optional.of(detail));

        GetShowDetailUseCase.Output output = useCase.execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output).isEqualTo(detail);
        verify(showDetailQueryRepository).findShowDetail(1L);
    }

    @Test
    void 공연_상세가_없으면_not_found_data_예외를_던진다() {
        when(showDetailQueryRepository.findShowDetail(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetShowDetailUseCase.Input(1L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.NOT_FOUND_DATA));
    }

    @Test
    void null_input_throws_invalid_request() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }

    @Test
    void null_show_id_throws_invalid_request() {
        assertThatThrownBy(() -> new GetShowDetailUseCase.Input(null))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.INVALID_REQUEST));
    }
}
