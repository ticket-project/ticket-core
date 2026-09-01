package com.ticket.core.app.order.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.order.model.OrderState;
import com.ticket.core.app.order.query.model.OrderDetailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetOrderDetailUseCaseTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-15T10:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderReadRepository orderReadRepository;

    private GetOrderDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOrderDetailUseCase(orderReadRepository, FIXED_CLOCK);
    }

    @Test
    void 단일_조회결과를_주문상세로_조합한다() {
        when(orderReadRepository.findDetailRows("order-key", 1L))
                .thenReturn(List.of(row(null)));

        GetOrderDetailUseCase.Output output = useCase.execute(
                new GetOrderDetailUseCase.Input("order-key", 1L)
        );

        assertThat(output.orderKey()).isEqualTo("order-key");
        assertThat(output.show().title()).isEqualTo("뮤지컬");
        assertThat(output.performance().venueName()).isEqualTo("올림픽홀");
        assertThat(output.booker().email()).isEqualTo("user@example.com");
        assertThat(output.price().ticketAmount()).isEqualByComparingTo("120000");
        assertThat(output.tickets().count()).isEqualTo(1);
        assertThat(output.tickets().seats().getFirst().label()).isEqualTo("1F A구역 10열 7번");
        assertThat(output.remainingSeconds()).isEqualTo(600L);
    }

    @Test
    void 본인_주문이_없으면_권한예외를_던진다() {
        when(orderReadRepository.findDetailRows("missing", 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute(new GetOrderDetailUseCase.Input("missing", 1L)))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType())
                        .isEqualTo(ErrorType.ORDER_NOT_OWNED));
    }

    @Test
    void 탈퇴한_회원의_주문이면_조회하지_않는다() {
        when(orderReadRepository.findDetailRows("order-key", 1L))
                .thenReturn(List.of(row(LocalDateTime.of(2026, 3, 1, 0, 0))));

        assertThatThrownBy(() -> useCase.execute(new GetOrderDetailUseCase.Input("order-key", 1L)))
                .isInstanceOf(CoreException.class);
    }

    private OrderDetailRow row(final LocalDateTime memberDeletedAt) {
        return new OrderDetailRow(
                "order-key",
                OrderState.PENDING,
                LocalDateTime.of(2026, 3, 15, 19, 10),
                100L,
                "뮤지컬",
                "show.png",
                10L,
                1L,
                LocalDateTime.of(2026, 3, 20, 19, 30),
                "올림픽홀",
                1L,
                "홍길동",
                "user@example.com",
                memberDeletedAt,
                501L,
                42L,
                1,
                "A",
                "10",
                "7",
                BigDecimal.valueOf(120000)
        );
    }
}
