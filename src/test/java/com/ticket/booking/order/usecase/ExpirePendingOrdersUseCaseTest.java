package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

import com.ticket.booking.order.usecase.ExpireOrderUseCase.OrderExpirationFailedException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class ExpirePendingOrdersUseCaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 15, 10, 0);

    @Mock
    private ExpireOrderUseCase expireOrderUseCase;

    private ExpirePendingOrdersUseCase useCase;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        final Clock clock = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        meterRegistry = new SimpleMeterRegistry();
        useCase = new ExpirePendingOrdersUseCase(expireOrderUseCase, clock, meterRegistry);
    }

    @Test
    void 만료_대상이_없으면_바로_끝난다() {
        when(expireOrderUseCase.expireNext(NOW, 0L)).thenReturn(Optional.empty());

        assertThat(useCase.execute()).isEqualTo(new ExpirePendingOrdersUseCase.Output(0, 0));
    }

    @Test
    void 만료한_주문의_id를_커서로_넘기며_끝까지_처리한다() {
        when(expireOrderUseCase.expireNext(NOW, 0L)).thenReturn(Optional.of(expired(3L)));
        when(expireOrderUseCase.expireNext(NOW, 3L)).thenReturn(Optional.of(expired(7L)));
        when(expireOrderUseCase.expireNext(NOW, 7L)).thenReturn(Optional.empty());

        assertThat(useCase.execute()).isEqualTo(new ExpirePendingOrdersUseCase.Output(2, 0));
        assertThat(meterRegistry.get("booking.order.expiration.delay").timer().count())
                .isEqualTo(2);
    }

    /** 실패한 주문은 롤백으로 다시 첫 대상이 되므로, 커서로 지나가야 뒤의 주문이 처리된다. */
    @Test
    void 실패한_주문은_건너뛰고_뒤의_주문을_처리한다() {
        when(expireOrderUseCase.expireNext(NOW, 0L))
                .thenThrow(new OrderExpirationFailedException(3L, new IllegalStateException("boom")));
        when(expireOrderUseCase.expireNext(NOW, 3L)).thenReturn(Optional.of(expired(7L)));
        when(expireOrderUseCase.expireNext(NOW, 7L)).thenReturn(Optional.empty());

        assertThat(useCase.execute()).isEqualTo(new ExpirePendingOrdersUseCase.Output(1, 1));
        assertThat(meterRegistry
                        .get("booking.order.expiration.failures")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    void 어느_주문인지_모르는_실패는_이번_순회를_멈춘다() {
        when(expireOrderUseCase.expireNext(NOW, 0L)).thenThrow(new IllegalStateException("db down"));

        assertThat(useCase.execute()).isEqualTo(new ExpirePendingOrdersUseCase.Output(0, 1));
        verify(expireOrderUseCase).expireNext(NOW, 0L);
        verifyNoMoreInteractions(expireOrderUseCase);
    }

    private ExpireOrderUseCase.ExpiredOrder expired(final long orderId) {
        return new ExpireOrderUseCase.ExpiredOrder(orderId, NOW.minusSeconds(3));
    }
}
