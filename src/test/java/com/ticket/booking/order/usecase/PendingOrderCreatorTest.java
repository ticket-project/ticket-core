package com.ticket.booking.order.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.OrderStarted;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderRepository;
import com.ticket.booking.order.domain.OrderSeat;
import com.ticket.booking.order.domain.OrderState;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.show.api.PerformanceSaleSnapshot;

/**
 * 예매 시작의 DB 구간을 고정한다.
 *
 * <p>옛 {@code OrderCreator}(주문 조립)와 옛 {@code CreatePendingOrderTransactionService}(저장·이력·event 발행과 트랜잭션 경계)가 이 한 클래스로
 * 합쳐졌다. 그래서 두 테스트가 각각 고정하던 것을 여기서 함께 본다 — 조립 결과(orderKey, PENDING, 표시 snapshot, 좌석 단가 합계)와 한 트랜잭션 안의 순서(저장 → 이력 →
 * publication).
 *
 * <p>옛 {@code OrderCreatorTest}의 "조립만 하고 저장하지 않는다"는 이 병합으로 뜻이 뒤집혔다. 지금은 저장과 트랜잭션 경계를 이 클래스가 가지므로, 그 자리를
 * {@link #주문_생성_메서드는_트랜잭션으로_실행된다}가 대신한다.
 */
@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class PendingOrderCreatorTest {
    private static final Duration HOLD_DURATION = Duration.ofSeconds(600);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 3, 15, 12, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHoldHistoryRecorder orderHoldHistoryRecorder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PendingOrderCreator pendingOrderCreator;

    @BeforeEach
    void setUp() {
        pendingOrderCreator =
                new PendingOrderCreator(orderRepository, orderHoldHistoryRecorder, eventPublisher, FIXED_CLOCK);
    }

    @Test
    void 주문과_hold_이력을_같은_트랜잭션에_저장하고_OrderStarted를_발행한다() {
        final PerformanceSeat seat = performanceSeat(501L, 201L, 1L, BigDecimal.TEN);
        final List<PerformanceSeat> seats = List.of(seat);
        final Hold hold = hold();
        saveAssigningId(55L);

        final String orderKey = pendingOrderCreator.create(20L, 10L, HOLD_DURATION, hold, seats, saleSnapshot());

        assertThat(orderKey).startsWith("ORDER-");
        final InOrder inOrder = inOrder(orderRepository, orderHoldHistoryRecorder, eventPublisher);
        // 조립과 저장, 트랜잭션 경계가 이제 한 클래스에 있다.
        inOrder.verify(orderRepository).save(any(Order.class));
        inOrder.verify(orderHoldHistoryRecorder)
                .recordCreated(20L, 10L, "hold-key", EXPIRES_AT.minusSeconds(600), EXPIRES_AT, seats);
        final ArgumentCaptor<OrderStarted> captor = ArgumentCaptor.forClass(OrderStarted.class);
        inOrder.verify(eventPublisher).publishEvent(captor.capture());
        final OrderStarted event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(55L);
        assertThat(event.memberId()).isEqualTo(20L);
        assertThat(event.holdKey()).isEqualTo("hold-key");
        assertThat(event.performanceSeatIds()).isEqualTo(Set.of(501L));
        assertThat(event.schemaVersion()).isEqualTo(OrderStarted.SCHEMA_VERSION);
        assertThat(event.occurredAt())
                .isEqualTo(EXPIRES_AT
                        .minusSeconds(600)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toInstant());
    }

    /** 옛 {@code OrderCreatorTest}의 조립 계약이다. 금액은 show의 표시값이 아니라 좌석 단가만으로 누적한다(ADR 0005). */
    @Test
    void 좌석_단가_합계로_pending_주문과_orderSeat를_조립한다() {
        final PerformanceSeat firstSeat = performanceSeat(101L, 201L, 1L, BigDecimal.TEN);
        final PerformanceSeat secondSeat = performanceSeat(102L, 202L, 2L, BigDecimal.valueOf(20));
        saveAssigningId(55L);

        final String orderKey = pendingOrderCreator.create(
                1L, 10L, HOLD_DURATION, hold(), List.of(firstSeat, secondSeat), saleSnapshot());

        final Order order = savedOrder();
        // 없어진 OrderKeyGeneratorTest가 고정하던 주문 키 형식이다.
        assertThat(orderKey).startsWith("ORDER-");
        assertThat(orderKey.substring("ORDER-".length())).hasSize(32).doesNotContain("-");
        assertThat(order.getOrderKey()).isEqualTo(orderKey);
        assertThat(order.getStatus()).isEqualTo(OrderState.PENDING);
        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.getPerformanceId()).isEqualTo(10L);
        assertThat(order.getHoldKey()).isEqualTo("hold-key");
        assertThat(order.getExpiresAt()).isEqualTo(EXPIRES_AT);
        // show가 준 표시값은 주문 생성 시점 그대로 박제된다(ADR 0005).
        assertThat(order.getShowTitleSnapshot()).isEqualTo("show-title");
        assertThat(order.getVenueNameSnapshot()).isEqualTo("venue-name");
        assertThat(order.getPerformanceStartAtSnapshot()).isEqualTo(LocalDateTime.of(2026, 3, 20, 19, 0));
        // 총액은 따로 더하지 않고 Order.addOrderSeat가 좌석 단가를 누적한 결과다.
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30");
        // 좌석은 별도 Repository가 아니라 Order aggregate가 들고 있고, cascade로 함께 저장된다.
        assertThat(order.getOrderSeats()).hasSize(2);
        assertThat(order.getOrderSeats()).extracting(OrderSeat::getSeatId).containsExactly(201L, 202L);
        assertThat(order.getOrderSeats())
                .extracting(OrderSeat::getPerformanceSeatId)
                .containsExactly(101L, 102L);
        assertThat(order.getOrderSeats())
                .extracting(OrderSeat::getUnitPrice)
                .containsExactly(BigDecimal.TEN, BigDecimal.valueOf(20));
        assertThat(order.getOrderSeats())
                .extracting(
                        OrderSeat::getGradeCodeSnapshot,
                        OrderSeat::getGradeNameSnapshot,
                        OrderSeat::getSeatLabelSnapshot)
                .containsExactly(
                        // 좌석 라벨은 SeatInfo.label()이 만든다 -- seatNo 그대로가 아니다.
                        tuple("VIP", "VIP석", "1F 가구역 A열 1번"), tuple("R", "R석", "1F 가구역 A열 2번"));
        assertThat(order.getOrderSeats())
                .allSatisfy(orderSeat -> assertThat(orderSeat.getOrder()).isSameAs(order));
    }

    @Test
    void 주문키는_주문마다_새로_생성한다() {
        final List<PerformanceSeat> seats = List.of(performanceSeat(101L, 201L, 1L, BigDecimal.TEN));
        saveAssigningId(55L);

        final String first = pendingOrderCreator.create(1L, 10L, HOLD_DURATION, hold(), seats, saleSnapshot());
        final String second = pendingOrderCreator.create(1L, 10L, HOLD_DURATION, hold(), seats, saleSnapshot());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 주문_좌석_컬렉션은_밖에서_직접_바꿀_수_없다() {
        final Order order = new Order(
                1L,
                10L,
                "ORDER-KEY",
                "hold-key",
                EXPIRES_AT,
                "show-title",
                LocalDateTime.of(2026, 3, 20, 19, 0),
                "venue-name");

        assertThatThrownBy(() -> order.getOrderSeats().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 좌석_표시값이_없으면_주문_생성에_실패한다() {
        final PerformanceSeat seat = performanceSeat(101L, 201L, 1L, BigDecimal.TEN);

        assertThatThrownBy(() ->
                        pendingOrderCreator.create(1L, 10L, HOLD_DURATION, hold(), List.of(seat), emptySaleSnapshot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("좌석 표시값");

        verifyNoInteractions(orderRepository, orderHoldHistoryRecorder, eventPublisher);
    }

    @Test
    void 등급_표시값이_없으면_주문_생성에_실패한다() {
        final PerformanceSeat seat = performanceSeat(101L, 201L, 99L, BigDecimal.TEN);

        assertThatThrownBy(
                        () -> pendingOrderCreator.create(1L, 10L, HOLD_DURATION, hold(), List.of(seat), saleSnapshot()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("등급 표시값");

        verifyNoInteractions(orderRepository, orderHoldHistoryRecorder, eventPublisher);
    }

    /** 저장 책임이 이 클래스로 모였으므로, 저장 실패의 전파도 여기서 고정한다. */
    @Test
    void 주문_저장_실패는_그대로_전파한다() {
        final PerformanceSeat seat = performanceSeat(101L, 201L, 1L, BigDecimal.TEN);
        when(orderRepository.save(any(Order.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() ->
                        pendingOrderCreator.create(20L, 10L, HOLD_DURATION, hold(), List.of(seat), saleSnapshot()))
                .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(orderHoldHistoryRecorder, eventPublisher);
    }

    @Test
    void 주문_생성_메서드는_트랜잭션으로_실행된다() throws NoSuchMethodException {
        assertThat(PendingOrderCreator.class
                        .getDeclaredMethod(
                                "create",
                                Long.class,
                                Long.class,
                                Duration.class,
                                Hold.class,
                                List.class,
                                PerformanceSaleSnapshot.class)
                        .isAnnotationPresent(Transactional.class))
                .isTrue();
    }

    private void saveAssigningId(final long id) {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            final Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", id);
            return order;
        });
    }

    private Order savedOrder() {
        final ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private Hold hold() {
        return new Hold("hold-key", 20L, 10L, List.of(7L), EXPIRES_AT);
    }

    private PerformanceSeat performanceSeat(
            final Long performanceSeatId,
            final Long seatId,
            final Long performanceGradeId,
            final BigDecimal unitPrice) {
        final PerformanceSeat performanceSeat = mock(PerformanceSeat.class);
        lenient().when(performanceSeat.getId()).thenReturn(performanceSeatId);
        lenient().when(performanceSeat.getSeatId()).thenReturn(seatId);
        lenient().when(performanceSeat.getPerformanceGradeId()).thenReturn(performanceGradeId);
        lenient().when(performanceSeat.getUnitPrice()).thenReturn(unitPrice);
        return performanceSeat;
    }

    private PerformanceSaleSnapshot saleSnapshot() {
        return new PerformanceSaleSnapshot(
                10L,
                1L,
                "show-title",
                1L,
                "venue-name",
                LocalDateTime.of(2026, 3, 20, 19, 0),
                Map.of(
                        201L,
                        new PerformanceSaleSnapshot.SeatInfo(201L, 1, "가", "A", "1"),
                        202L,
                        new PerformanceSaleSnapshot.SeatInfo(202L, 1, "가", "A", "2")),
                Map.of(
                        1L,
                        new PerformanceSaleSnapshot.GradeInfo(1L, "VIP", "VIP석", 1, BigDecimal.TEN),
                        2L,
                        new PerformanceSaleSnapshot.GradeInfo(2L, "R", "R석", 2, BigDecimal.valueOf(20))));
    }

    private PerformanceSaleSnapshot emptySaleSnapshot() {
        return new PerformanceSaleSnapshot(
                10L, 1L, "show-title", 1L, "venue-name", LocalDateTime.of(2026, 3, 20, 19, 0), Map.of(), Map.of());
    }
}
