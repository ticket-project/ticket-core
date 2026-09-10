package com.ticket.booking.application;

import com.ticket.booking.domain.OrderKeyGenerator;
import com.ticket.booking.domain.Order;
import com.ticket.booking.domain.OrderSeat;
import com.ticket.booking.domain.OrderRepository;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.show.PerformanceSaleSnapshot;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class OrderCreatorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderKeyGenerator orderKeyGenerator;

    @InjectMocks
    private OrderCreator orderCreator;

    @Test
    void 좌석_가격_합계로_pending_주문과_orderSeat를_생성한다() {
        // given
        final PerformanceSeat firstSeat = createPerformanceSeat(101L, 201L, 1L, BigDecimal.TEN);
        final PerformanceSeat secondSeat = createPerformanceSeat(102L, 202L, 2L, BigDecimal.valueOf(20));
        when(orderKeyGenerator.generate()).thenReturn("ORDER-KEY");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            final Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 99L);
            return order;
        });
        final PerformanceSaleSnapshot saleSnapshot = new PerformanceSaleSnapshot(
                10L, 1L, "show-title", 1L, "venue-name", LocalDateTime.of(2026, 3, 20, 19, 0),
                java.util.Map.of(
                        201L, new PerformanceSaleSnapshot.SeatInfo(201L, 1, "가", "A", "1"),
                        202L, new PerformanceSaleSnapshot.SeatInfo(202L, 1, "가", "A", "2")
                ),
                java.util.Map.of(
                        1L, new PerformanceSaleSnapshot.GradeInfo(1L, "VIP", "VIP석", 1, BigDecimal.TEN),
                        2L, new PerformanceSaleSnapshot.GradeInfo(2L, "R", "R석", 2, BigDecimal.valueOf(20))
                )
        );

        // when
        final Order order = orderCreator.createPendingOrder(
                1L,
                10L,
                "hold-key",
                LocalDateTime.of(2026, 3, 15, 12, 0),
                List.of(firstSeat, secondSeat),
                saleSnapshot
        );

        // then
        assertThat(order.getOrderKey()).isEqualTo("ORDER-KEY");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30");
        // 좌석은 별도 Repository가 아니라 Order aggregate가 들고 있고, cascade로 함께 저장된다.
        assertThat(order.getOrderSeats()).hasSize(2);
        assertThat(order.getOrderSeats()).extracting(OrderSeat::getSeatId).containsExactly(201L, 202L);
        assertThat(order.getOrderSeats()).allSatisfy(orderSeat ->
                assertThat(orderSeat.getOrder()).isSameAs(order));
    }

    @Test
    void 주문_좌석_컬렉션은_밖에서_직접_바꿀_수_없다() {
        final Order order = new Order(
                1L, 10L, "ORDER-KEY", "hold-key", BigDecimal.TEN,
                LocalDateTime.of(2026, 3, 15, 12, 0), "show-title",
                LocalDateTime.of(2026, 3, 20, 19, 0), "venue-name"
        );

        assertThatThrownBy(() -> order.getOrderSeats().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pending_주문_저장_예외는_그대로_DataIntegrityViolationException_으로_전파한다() {
        // given
        when(orderKeyGenerator.generate()).thenReturn("ORDER-KEY");
        final DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate",
                new ConstraintViolationException("duplicate", new SQLException("duplicate"), "", "UK_ORDERS_PENDING_MEMBER_PERF")
        );
        when(orderRepository.save(any(Order.class))).thenThrow(exception);

        // when
        // then
        assertThatThrownBy(() -> orderCreator.createPendingOrder(
                1L,
                10L,
                "hold-key",
                LocalDateTime.now(),
                List.of(priceOnlyPerformanceSeat(BigDecimal.TEN)),
                emptySaleSnapshot()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_데이터_무결성_예외도_그대로_전파한다() {
        // given
        when(orderKeyGenerator.generate()).thenReturn("ORDER-KEY");
        when(orderRepository.save(any(Order.class))).thenThrow(new DataIntegrityViolationException("other"));

        // when
        // then
        assertThatThrownBy(() -> orderCreator.createPendingOrder(
                1L,
                10L,
                "hold-key",
                LocalDateTime.now(),
                List.of(priceOnlyPerformanceSeat(BigDecimal.TEN)),
                emptySaleSnapshot()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private PerformanceSeat createPerformanceSeat(
            final Long performanceSeatId, final Long seatId, final Long performanceGradeId, final BigDecimal price
    ) {
        final PerformanceSeat performanceSeat = org.mockito.Mockito.mock(PerformanceSeat.class);
        when(performanceSeat.getId()).thenReturn(performanceSeatId);
        when(performanceSeat.getUnitPrice()).thenReturn(price);
        when(performanceSeat.getSeatId()).thenReturn(seatId);
        when(performanceSeat.getPerformanceGradeId()).thenReturn(performanceGradeId);
        return performanceSeat;
    }

    private PerformanceSeat priceOnlyPerformanceSeat(final BigDecimal price) {
        final PerformanceSeat performanceSeat = org.mockito.Mockito.mock(PerformanceSeat.class);
        when(performanceSeat.getUnitPrice()).thenReturn(price);
        return performanceSeat;
    }

    private PerformanceSaleSnapshot emptySaleSnapshot() {
        return new PerformanceSaleSnapshot(
                10L, 1L, "show-title", 1L, "venue-name", LocalDateTime.now(),
                java.util.Map.of(), java.util.Map.of()
        );
    }
}
