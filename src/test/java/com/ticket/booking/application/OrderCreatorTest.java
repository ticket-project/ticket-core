package com.ticket.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.domain.order.Order;
import com.ticket.booking.domain.order.OrderKeyGenerator;
import com.ticket.booking.domain.order.OrderRepository;
import com.ticket.booking.domain.order.OrderSeat;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.show.PerformanceSaleSnapshot;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class OrderCreatorTest {
    @Mock private OrderKeyGenerator orderKeyGenerator;
    @InjectMocks private OrderCreator orderCreator;

    @Test
    void 좌석_가격_합계로_pending_주문과_orderSeat를_조립한다() {
        // given
        final PerformanceSeat firstSeat = createPerformanceSeat(101L, 201L, 1L, BigDecimal.TEN);
        final PerformanceSeat secondSeat =
                createPerformanceSeat(102L, 202L, 2L, BigDecimal.valueOf(20));
        when(orderKeyGenerator.generate()).thenReturn("ORDER-KEY");
        final PerformanceSaleSnapshot saleSnapshot =
                new PerformanceSaleSnapshot(
                        10L,
                        1L,
                        "show-title",
                        1L,
                        "venue-name",
                        LocalDateTime.of(2026, 3, 20, 19, 0),
                        java.util.Map.of(
                                201L,
                                new PerformanceSaleSnapshot.SeatInfo(201L, 1, "가", "A", "1"),
                                202L,
                                new PerformanceSaleSnapshot.SeatInfo(202L, 1, "가", "A", "2")),
                        java.util.Map.of(
                                1L,
                                new PerformanceSaleSnapshot.GradeInfo(
                                        1L, "VIP", "VIP석", 1, BigDecimal.TEN),
                                2L,
                                new PerformanceSaleSnapshot.GradeInfo(
                                        2L, "R", "R석", 2, BigDecimal.valueOf(20))));
        // when
        final Order order =
                orderCreator.createPendingOrder(
                        1L,
                        10L,
                        "hold-key",
                        LocalDateTime.of(2026, 3, 15, 12, 0),
                        List.of(firstSeat, secondSeat),
                        saleSnapshot);
        // then
        assertThat(order.getOrderKey()).isEqualTo("ORDER-KEY");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30");
        // 좌석은 별도 Repository가 아니라 Order aggregate가 들고 있고, cascade로 함께 저장된다.
        assertThat(order.getOrderSeats()).hasSize(2);
        assertThat(order.getOrderSeats())
                .extracting(OrderSeat::getSeatId)
                .containsExactly(201L, 202L);
        assertThat(order.getOrderSeats())
                .allSatisfy(orderSeat -> assertThat(orderSeat.getOrder()).isSameAs(order));
    }

    @Test
    void 주문_좌석_컬렉션은_밖에서_직접_바꿀_수_없다() {
        final Order order =
                new Order(
                        1L,
                        10L,
                        "ORDER-KEY",
                        "hold-key",
                        LocalDateTime.of(2026, 3, 15, 12, 0),
                        "show-title",
                        LocalDateTime.of(2026, 3, 20, 19, 0),
                        "venue-name");

        assertThatThrownBy(() -> order.getOrderSeats().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 조립과 저장을 분리한 결과를 고정한다. 저장과 트랜잭션 경계는 {@link CreatePendingOrderTransactionService}의 것이라, 이 클래스는
     * Repository를 알지도 {@code @Transactional}을 갖지도 않는다.
     */
    @Test
    void 조립만_하고_저장하지_않는다() {
        assertThat(Arrays.stream(OrderCreator.class.getDeclaredFields()))
                .noneSatisfy(field -> assertThat(field.getType()).isEqualTo(OrderRepository.class));
        assertThat(Arrays.stream(OrderCreator.class.getDeclaredMethods()))
                .noneMatch(method -> method.isAnnotationPresent(Transactional.class));
        assertThat(OrderCreator.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void 좌석_표시값이_없으면_조립에_실패한다() {
        when(orderKeyGenerator.generate()).thenReturn("ORDER-KEY");
        final PerformanceSeat seat = createPerformanceSeat(101L, 201L, 1L, BigDecimal.TEN);

        assertThatThrownBy(
                        () ->
                                orderCreator.createPendingOrder(
                                        1L,
                                        10L,
                                        "hold-key",
                                        LocalDateTime.of(2026, 3, 15, 12, 0),
                                        List.of(seat),
                                        emptySaleSnapshot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("좌석 표시값");
    }

    private PerformanceSeat createPerformanceSeat(
            final Long performanceSeatId,
            final Long seatId,
            final Long performanceGradeId,
            final BigDecimal price) {
        final PerformanceSeat performanceSeat = org.mockito.Mockito.mock(PerformanceSeat.class);
        org.mockito.Mockito.lenient().when(performanceSeat.getId()).thenReturn(performanceSeatId);
        org.mockito.Mockito.lenient().when(performanceSeat.getUnitPrice()).thenReturn(price);
        org.mockito.Mockito.lenient().when(performanceSeat.getSeatId()).thenReturn(seatId);
        org.mockito.Mockito.lenient()
                .when(performanceSeat.getPerformanceGradeId())
                .thenReturn(performanceGradeId);
        return performanceSeat;
    }

    private PerformanceSaleSnapshot emptySaleSnapshot() {
        return new PerformanceSaleSnapshot(
                10L,
                1L,
                "show-title",
                1L,
                "venue-name",
                LocalDateTime.now(),
                java.util.Map.of(),
                java.util.Map.of());
    }
}
