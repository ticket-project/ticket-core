package com.ticket.booking.application;

import com.ticket.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Input 계약은 HTTP를 거치지 않고도 성립해야 하므로 API 없이 직접 생성해 검증한다.
 */
@SuppressWarnings("NonAsciiCharacters")
class CreateOrderInputTest {

    @Test
    void performanceId가_유효하지_않으면_invalid_input_예외를_던진다() {
        assertInvalidInput(() -> new CreateOrderUseCase.Input(null, List.of(1L), 10L, null));
        assertInvalidInput(() -> new CreateOrderUseCase.Input(0L, List.of(1L), 10L, null));
    }

    @Test
    void memberId가_유효하지_않으면_invalid_input_예외를_던진다() {
        assertInvalidInput(() -> new CreateOrderUseCase.Input(1L, List.of(1L), null, null));
        assertInvalidInput(() -> new CreateOrderUseCase.Input(1L, List.of(1L), -1L, null));
    }

    /**
     * 좌석 목록의 null·빈 목록·중복은 도메인 불변식(RequestedSeatIds)이 판정한다.
     * 같은 규칙을 Input에서 다시 실행하지 않는다.
     */
    @Test
    void 좌석_목록은_Input이_판정하지_않는다() {
        assertThatCode(() -> new CreateOrderUseCase.Input(1L, null, 10L, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> new CreateOrderUseCase.Input(1L, List.of(), 10L, null))
                .doesNotThrowAnyException();
    }

    @Test
    void admissionToken은_대기열이_필요한_회차에서만_쓰이므로_필수가_아니다() {
        assertThatCode(() -> new CreateOrderUseCase.Input(1L, List.of(1L), 10L, null))
                .doesNotThrowAnyException();
    }

    private void assertInvalidInput(final Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(InvalidRequestException.class);
    }
}
