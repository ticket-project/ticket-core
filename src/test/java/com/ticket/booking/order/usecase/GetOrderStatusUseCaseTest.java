package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.exception.OrderNotOwnedException;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderState;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetOrderStatusUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private OrderRepository repository;

    private GetOrderStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOrderStatusUseCase(repository, CLOCK);
    }

    @Test
    void 결제대기_주문의_남은시간을_반환한다() {
        when(repository.findByOrderKeyAndMemberId("order-key", 1L)).thenReturn(Optional.of(order()));

        GetOrderStatusUseCase.Output output = useCase.execute(new GetOrderStatusUseCase.Input("order-key", 1L));

        assertThat(output.status()).isEqualTo(OrderState.PENDING);
        assertThat(output.remainingSeconds()).isEqualTo(600L);
    }

    @Test
    void 본인_주문이_없으면_권한예외를_던진다() {
        when(repository.findByOrderKeyAndMemberId("missing", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetOrderStatusUseCase.Input("missing", 1L)))
                .isInstanceOf(OrderNotOwnedException.class)
                .hasFieldOrPropertyWithValue("orderKey", "missing")
                .hasFieldOrPropertyWithValue("memberId", 1L);
    }

    private Order order() {
        return new Order(
                1L,
                10L,
                "order-key",
                "hold-key",
                LocalDateTime.of(2026, 3, 15, 19, 10),
                "뮤지컬",
                LocalDateTime.of(2026, 3, 20, 19, 30),
                "올림픽홀");
    }
}
