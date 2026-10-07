package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class OrderHoldSnapshotReaderTest {
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderHoldSnapshotReader reader;

    @Test
    void 주문_좌석_순서를_유지하고_저장된_편성_식별자를_매핑한다() {
        final LocalDateTime expiresAt = LocalDateTime.of(2026, 10, 1, 10, 10);
        final Order order = new Order(1L, 10L, "order-key", "hold-key", expiresAt, "공연", expiresAt, "공연장");
        order.addOrderSeat(902L, 20L, BigDecimal.TEN, "R", "R석", "20번");
        order.addOrderSeat(901L, 10L, BigDecimal.TEN, "R", "R석", "10번");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        final OrderHoldSnapshot snapshot = reader.read(1L).orElseThrow();

        assertThat(snapshot.performanceId()).isEqualTo(10L);
        assertThat(snapshot.seatIds()).containsExactly(20L, 10L);
        assertThat(snapshot.performanceSeatIdBySeatId())
                .containsExactlyInAnyOrderEntriesOf(Map.of(20L, 902L, 10L, 901L));
    }

    @Test
    void 주문이_없으면_snapshot도_없다() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(reader.read(1L)).isEmpty();
    }
}
