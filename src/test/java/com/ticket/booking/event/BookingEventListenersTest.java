package com.ticket.booking.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;

import com.ticket.booking.OrderCreated;
import com.ticket.booking.OrderTerminated;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderSeat;
import com.ticket.booking.order.usecase.OrderHoldSnapshot;
import com.ticket.booking.order.usecase.OrderHoldSnapshotReader;

/**
 * listener 멱등성과 stale-event 방어를 고정한다.
 *
 * <p>{@link BookingEventListeners}는 event payload를 그대로 믿지 않고 {@code orderId}로 현재 저장된 order·orderSeat를 다시 읽는다. hold
 * 생성·해제 자체의 멱등 로직은 {@code HoldCreationCoordinatorTest}/{@code HoldReleaseCoordinatorTest}가 이미 고정하므로, 여기서는 listener가 그
 * 로직에 올바른 입력을 넘기는지와 존재하지 않는 주문에 대해 아무 부수효과도 일으키지 않는지를 본다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class BookingEventListenersTest {
    @Mock
    private OrderHoldSnapshotReader orderHoldSnapshotReader;

    @Mock
    private HoldCreationCoordinator holdCreationCoordinator;

    @Mock
    private HoldReleaseCoordinator holdReleaseCoordinator;

    private BookingEventListeners listeners;

    @BeforeEach
    void setUp() {
        listeners = new BookingEventListeners(orderHoldSnapshotReader, holdCreationCoordinator, holdReleaseCoordinator);
    }

    /** listener 자체가 DB 트랜잭션을 열면 Redis·WebSocket 작업이 booking connection을 쥔 채로 실행된다. */
    @Test
    void listener는_자기_DB_트랜잭션을_열지_않는다() throws NoSuchMethodException {
        assertThat(BookingEventListeners.class
                        .getDeclaredMethod("on", OrderCreated.class)
                        .getAnnotation(ApplicationModuleListener.class)
                        .propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(BookingEventListeners.class
                        .getDeclaredMethod("on", OrderTerminated.class)
                        .getAnnotation(ApplicationModuleListener.class)
                        .propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void OrderCreated_주문이_없으면_아무_후처리도_하지_않는다() {
        final OrderCreated event = orderCreated(10L, "hold-key");
        when(orderHoldSnapshotReader.read(10L)).thenReturn(Optional.empty());

        listeners.on(event);

        verifyNoInteractions(holdCreationCoordinator);
    }

    @Test
    void OrderCreated는_현재_DB_상태로_Hold를_재구성해_생성_프로세서에_넘긴다() {
        final OrderCreated event = orderCreated(10L, "hold-key");
        final Order order = order(10L, 200L, "hold-key", LocalDateTime.of(2026, 3, 15, 10, 10));
        addOrderSeat(order, 501L, 42L);
        addOrderSeat(order, 502L, 43L);
        when(orderHoldSnapshotReader.read(10L)).thenReturn(Optional.of(snapshotOf(order)));

        listeners.on(event);

        final Hold expectedHold = new Hold("hold-key", 20L, 200L, List.of(42L, 43L), order.getExpiresAt());
        verify(holdCreationCoordinator).clearSelectionsAndPublishHeld(expectedHold, Map.of(42L, 501L, 43L, 502L));
    }

    @Test
    void OrderTerminated_주문이_없으면_아무_후처리도_하지_않는다() {
        final OrderTerminated event = orderTerminated(11L, "hold-key");
        when(orderHoldSnapshotReader.read(11L)).thenReturn(Optional.empty());

        listeners.on(event);

        verifyNoInteractions(holdReleaseCoordinator);
    }

    @Test
    void OrderTerminated는_현재_DB_상태로_해제_입력을_만들어_해제_프로세서에_넘긴다() {
        final OrderTerminated event = orderTerminated(11L, "hold-key");
        final Order order = order(11L, 200L, "hold-key", LocalDateTime.of(2026, 3, 15, 10, 10));
        addOrderSeat(order, 501L, 42L);
        when(orderHoldSnapshotReader.read(11L)).thenReturn(Optional.of(snapshotOf(order)));

        listeners.on(event);

        verify(holdReleaseCoordinator)
                .releaseAndPublish(new HoldReleaseTask(200L, "hold-key", List.of(42L), Map.of(42L, 501L)));
    }

    /** listener는 entity가 아니라 짧은 읽기 트랜잭션에서 완성된 값을 받는다. */
    private OrderHoldSnapshot snapshotOf(final Order order) {
        return new OrderHoldSnapshot(
                order.getPerformanceId(),
                order.getOrderSeats().stream().map(OrderSeat::getSeatId).toList(),
                order.getExpiresAt(),
                order.getOrderSeats().stream()
                        .collect(Collectors.toMap(OrderSeat::getSeatId, OrderSeat::getPerformanceSeatId)));
    }

    private OrderCreated orderCreated(final long orderId, final String holdKey) {
        return new OrderCreated(
                UUID.randomUUID(), OrderCreated.SCHEMA_VERSION, orderId, 20L, holdKey, Set.of(501L), Instant.now());
    }

    private OrderTerminated orderTerminated(final long orderId, final String holdKey) {
        return new OrderTerminated(
                UUID.randomUUID(),
                OrderTerminated.SCHEMA_VERSION,
                orderId,
                20L,
                holdKey,
                Set.of(501L),
                "CANCELED",
                Instant.now());
    }

    private Order order(final Long id, final Long performanceId, final String holdKey, final LocalDateTime expiresAt) {
        final Order order = new Order(
                20L,
                performanceId,
                "order-" + id,
                holdKey,
                expiresAt,
                "show-title",
                expiresAt.minusDays(1),
                "venue-name");
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private void addOrderSeat(final Order order, final Long performanceSeatId, final Long seatId) {
        order.addOrderSeat(performanceSeatId, seatId, BigDecimal.TEN, "R", "R석", "1F 가구역 A열 1번");
    }
}
