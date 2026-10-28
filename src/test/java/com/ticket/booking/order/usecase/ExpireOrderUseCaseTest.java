package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.usecase.ExpireOrderUseCase.OrderExpirationFailedException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class ExpireOrderUseCaseTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 15, 10, 0);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderTerminationService orderTerminationService;

    @InjectMocks
    private ExpireOrderUseCase useCase;

    @Test
    void 잠글_만료_주문이_없으면_빈_값이다() {
        when(orderRepository.findNextExpirableForUpdateSkipLocked(NOW, 0L)).thenReturn(Optional.empty());

        assertThat(useCase.expireNext(NOW, 0L)).isEmpty();
        verifyNoInteractions(orderTerminationService);
    }

    @Test
    void 잠근_만료_주문을_만료하고_id를_돌려준다() {
        final Order order = order(10L, NOW.minusSeconds(1));
        when(orderRepository.findNextExpirableForUpdateSkipLocked(NOW, 3L)).thenReturn(Optional.of(order));

        assertThat(useCase.expireNext(NOW, 3L)).contains(new ExpireOrderUseCase.ExpiredOrder(10L, NOW.minusSeconds(1)));
        verify(orderTerminationService).expire(order, NOW);
    }

    /** 실패한 주문의 id가 있어야 청소가 그 주문을 지나 뒤로 넘어간다. */
    @Test
    void 만료가_실패하면_주문_id를_실은_예외를_던진다() {
        final Order order = order(10L, NOW.minusSeconds(1));
        when(orderRepository.findNextExpirableForUpdateSkipLocked(NOW, 0L)).thenReturn(Optional.of(order));
        doThrow(new IllegalStateException("boom")).when(orderTerminationService).expire(order, NOW);

        assertThatThrownBy(() -> useCase.expireNext(NOW, 0L))
                .isInstanceOfSatisfying(
                        OrderExpirationFailedException.class,
                        e -> assertThat(e.orderId()).isEqualTo(10L))
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void 만료_시각이_지난_PENDING이면_그_자리에서_만료한다() {
        final Order order = order(10L, NOW);
        when(orderRepository.findPendingByMemberIdAndPerformanceIdForUpdate(1L, 100L))
                .thenReturn(Optional.of(order));

        assertThat(useCase.expireIfStale(1L, 100L, NOW)).isTrue();
        verify(orderTerminationService).expire(order, NOW);
    }

    @Test
    void 아직_유효한_PENDING은_만료하지_않는다() {
        final Order order = order(10L, NOW.plusSeconds(1));
        when(orderRepository.findPendingByMemberIdAndPerformanceIdForUpdate(1L, 100L))
                .thenReturn(Optional.of(order));

        assertThat(useCase.expireIfStale(1L, 100L, NOW)).isFalse();
        verifyNoInteractions(orderTerminationService);
    }

    @Test
    void PENDING이_없으면_만료하지_않는다() {
        when(orderRepository.findPendingByMemberIdAndPerformanceIdForUpdate(1L, 100L))
                .thenReturn(Optional.empty());

        assertThat(useCase.expireIfStale(1L, 100L, NOW)).isFalse();
        verifyNoInteractions(orderTerminationService);
    }

    private Order order(final Long id, final LocalDateTime expiresAt) {
        final Order order = new Order(
                1L, 100L, "order-key", "hold-key", expiresAt, "show-title", expiresAt.plusDays(1), "venue-name");
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
