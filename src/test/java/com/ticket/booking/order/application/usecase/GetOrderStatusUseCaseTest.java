package com.ticket.booking.order.application.usecase;

import com.ticket.booking.order.application.port.OrderQueryPort;

import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.order.application.OrderStatusView;
import com.ticket.booking.order.exception.OrderNotOwnedException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.member.MemberLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class GetOrderStatusUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-03-15T10:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private OrderQueryPort repository;

    @Mock
    private MemberLookup memberLookup;

    private GetOrderStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOrderStatusUseCase(repository, memberLookup, CLOCK);
    }

    @Test
    void 결제대기_주문의_남은시간을_반환한다() {
        when(repository.findStatus("order-key", 1L)).thenReturn(Optional.of(new OrderStatusView(
                "order-key",
                OrderState.PENDING,
                LocalDateTime.of(2026, 3, 15, 19, 10)
        )));

        GetOrderStatusUseCase.Output output = useCase.execute(
                new GetOrderStatusUseCase.Input("order-key", 1L)
        );

        assertThat(output.status()).isEqualTo(OrderState.PENDING);
        assertThat(output.remainingSeconds()).isEqualTo(600L);
        verify(memberLookup).requireActive(1L);
    }

    @Test
    void 본인_주문이_없으면_권한예외를_던진다() {
        when(repository.findStatus("missing", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new GetOrderStatusUseCase.Input("missing", 1L)))
                .isInstanceOf(OrderNotOwnedException.class)
                .hasFieldOrPropertyWithValue("orderKey", "missing")
                .hasFieldOrPropertyWithValue("memberId", 1L);
    }

    @Test
    void 탈퇴한_회원의_주문상태는_조회하지_않는다() {
        when(repository.findStatus("order-key", 1L)).thenReturn(Optional.of(new OrderStatusView(
                "order-key",
                OrderState.PENDING,
                LocalDateTime.of(2026, 3, 15, 19, 10)
        )));
        doThrow(new NotFoundException()).when(memberLookup).requireActive(1L);

        assertThatThrownBy(() -> useCase.execute(new GetOrderStatusUseCase.Input("order-key", 1L)))
                .isInstanceOf(OrderNotOwnedException.class)
                .hasFieldOrPropertyWithValue("orderKey", "order-key")
                .hasFieldOrPropertyWithValue("memberId", 1L);
    }
}
