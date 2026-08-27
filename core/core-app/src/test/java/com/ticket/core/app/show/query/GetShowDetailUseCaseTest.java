package com.ticket.core.app.show.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.show.meta.SaleType;
import com.ticket.core.domain.show.BookingStatus;
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
    private ShowDetailReadRepository showDetailReadRepository;

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
        when(showDetailReadRepository.findShowDetail(1L)).thenReturn(Optional.of(detail));

        GetShowDetailUseCase.Output output = useCase.execute(new GetShowDetailUseCase.Input(1L));

        assertThat(output).isEqualTo(detail);
        verify(showDetailReadRepository).findShowDetail(1L);
    }

    @Test
    void 공연_상세가_없으면_not_found_data_예외를_던진다() {
        when(showDetailReadRepository.findShowDetail(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetShowDetailUseCase.Input(1L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ApplicationErrorType.DATA_NOT_FOUND));
    }

    /**
     * showId 계약은 Input 생성자 한곳에서만 판정한다. execute가 같은 검사를 반복하지 않는다.
     */
    @Test
    void showId가_유효하지_않으면_Input_생성에서_예외를_던진다() {
        assertThatThrownBy(() -> new GetShowDetailUseCase.Input(null))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ApplicationErrorType.INVALID_INPUT));
        assertThatThrownBy(() -> new GetShowDetailUseCase.Input(0L))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ApplicationErrorType.INVALID_INPUT));
    }

    /**
     * Input을 아예 넘기지 않은 것은 사용자 입력 오류가 아니라 호출부의 프로그래머 오류다.
     */
    @Test
    void execute에_Input을_넘기지_않으면_NPE가_난다() {
        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(NullPointerException.class);
    }
}
