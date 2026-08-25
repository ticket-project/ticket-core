package com.ticket.core.app.order.command;

import com.ticket.core.domain.order.command.create.OrderKeyGenerator;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.order.model.OrderSeat;
import com.ticket.core.domain.order.repository.OrderRepository;
import com.ticket.core.domain.order.repository.OrderSeatRepository;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import com.ticket.core.domain.seat.model.Seat;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class OrderCreatorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderSeatRepository orderSeatRepository;

    @Mock
    private OrderKeyGenerator orderKeyGenerator;

    @InjectMocks
    private OrderCreator orderCreator;

    @Test
    void 좌석_가격_합계로_pending_주문과_orderSeat를_생성한다() {
        // given
        final PerformanceSeat firstSeat = createPerformanceSeat(101L, 201L, BigDecimal.TEN);
        final PerformanceSeat secondSeat = createPerformanceSeat(102L, 202L, BigDecimal.valueOf(20));
        when(orderKeyGenerator.generate()).thenReturn("ORDER-KEY");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            final Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 99L);
            return order;
        });

        // when
        final Order order = orderCreator.createPendingOrder(
                1L,
                10L,
                "hold-key",
                LocalDateTime.of(2026, 3, 15, 12, 0),
                List.of(firstSeat, secondSeat)
        );

        // then
        assertThat(order.getOrderKey()).isEqualTo("ORDER-KEY");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30");
        final ArgumentCaptor<List<OrderSeat>> orderSeatCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderSeatRepository).saveAll(orderSeatCaptor.capture());
        assertThat(orderSeatCaptor.getValue()).hasSize(2);
        assertThat(orderSeatCaptor.getValue()).extracting(OrderSeat::getSeatId).containsExactly(201L, 202L);
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
                List.of(priceOnlyPerformanceSeat(BigDecimal.TEN))
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
                List.of(priceOnlyPerformanceSeat(BigDecimal.TEN))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private PerformanceSeat createPerformanceSeat(final Long performanceSeatId, final Long seatId, final BigDecimal price) {
        final PerformanceSeat performanceSeat = org.mockito.Mockito.mock(PerformanceSeat.class);
        final Seat seat = org.mockito.Mockito.mock(Seat.class);
        when(performanceSeat.getId()).thenReturn(performanceSeatId);
        when(performanceSeat.getPrice()).thenReturn(price);
        when(performanceSeat.getSeat()).thenReturn(seat);
        when(seat.getId()).thenReturn(seatId);
        return performanceSeat;
    }

    private PerformanceSeat priceOnlyPerformanceSeat(final BigDecimal price) {
        final PerformanceSeat performanceSeat = org.mockito.Mockito.mock(PerformanceSeat.class);
        when(performanceSeat.getPrice()).thenReturn(price);
        return performanceSeat;
    }
}
