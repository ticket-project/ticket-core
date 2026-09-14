package com.ticket.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
import com.ticket.booking.domain.hold.Hold;
import com.ticket.booking.domain.order.Order;
import com.ticket.booking.domain.order.OrderRepository;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.show.api.PerformanceSaleSnapshot;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CreatePendingOrderTransactionServiceTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    @Mock private OrderRepository orderRepository;
    @Mock private OrderCreator orderCreator;
    @Mock private OrderHoldHistoryRecorder orderHoldHistoryRecorder;
    @Mock private ApplicationEventPublisher eventPublisher;
    private CreatePendingOrderTransactionService service;

    @BeforeEach
    void setUp() {
        service =
                new CreatePendingOrderTransactionService(
                        orderRepository,
                        orderCreator,
                        orderHoldHistoryRecorder,
                        eventPublisher,
                        FIXED_CLOCK);
    }

    @Test
    void 주문과_hold_이력을_같은_트랜잭션에_저장하고_OrderStarted를_발행한다() {
        final Duration holdDuration = Duration.ofSeconds(600);
        final PerformanceSeat seat = mock(PerformanceSeat.class);
        when(seat.getId()).thenReturn(501L);
        final List<PerformanceSeat> seats = List.of(seat);
        final Hold hold =
                new Hold("hold-key", 20L, 10L, List.of(7L), LocalDateTime.of(2026, 3, 15, 12, 0));
        final Order order =
                new Order(
                        20L,
                        10L,
                        "order-key",
                        "hold-key",
                        hold.expiresAt(),
                        "show-title",
                        hold.expiresAt().plusDays(1),
                        "venue-name");
        ReflectionTestUtils.setField(order, "id", 55L);
        final PerformanceSaleSnapshot saleSnapshot =
                new PerformanceSaleSnapshot(
                        10L,
                        1L,
                        "show-title",
                        1L,
                        "venue-name",
                        hold.expiresAt().plusDays(1),
                        java.util.Map.of(),
                        java.util.Map.of());

        when(orderCreator.createPendingOrder(
                        20L, 10L, "hold-key", hold.expiresAt(), seats, saleSnapshot))
                .thenReturn(order);
        when(orderRepository.save(order)).thenReturn(order);

        final String orderKey = service.create(20L, 10L, holdDuration, hold, seats, saleSnapshot);

        assertThat(orderKey).isEqualTo("order-key");
        final InOrder inOrder =
                inOrder(orderCreator, orderRepository, orderHoldHistoryRecorder, eventPublisher);
        inOrder.verify(orderCreator)
                .createPendingOrder(20L, 10L, "hold-key", hold.expiresAt(), seats, saleSnapshot);
        // 조립은 OrderCreator가, 저장과 트랜잭션 경계는 이 서비스가 갖는다.
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(orderHoldHistoryRecorder)
                .recordCreated(
                        20L,
                        10L,
                        "hold-key",
                        hold.expiresAt().minusSeconds(600),
                        hold.expiresAt(),
                        seats);
        final ArgumentCaptor<OrderStarted> captor = ArgumentCaptor.forClass(OrderStarted.class);
        inOrder.verify(eventPublisher).publishEvent(captor.capture());
        final OrderStarted event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(55L);
        assertThat(event.memberId()).isEqualTo(20L);
        assertThat(event.holdKey()).isEqualTo("hold-key");
        assertThat(event.performanceSeatIds()).isEqualTo(Set.of(501L));
        assertThat(event.schemaVersion()).isEqualTo(OrderStarted.SCHEMA_VERSION);
        assertThat(event.occurredAt())
                .isEqualTo(
                        hold.expiresAt()
                                .minusSeconds(600)
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .toInstant());
    }

    /** 저장 책임이 이 트랜잭션 서비스로 모였으므로, 저장 실패의 전파도 여기서 고정한다. */
    @Test
    void 주문_저장_실패는_그대로_전파한다() {
        final PerformanceSeat seat = mock(PerformanceSeat.class);
        final List<PerformanceSeat> seats = List.of(seat);
        final Hold hold =
                new Hold("hold-key", 20L, 10L, List.of(7L), LocalDateTime.of(2026, 3, 15, 12, 0));
        final PerformanceSaleSnapshot saleSnapshot =
                new PerformanceSaleSnapshot(
                        10L,
                        1L,
                        "show-title",
                        1L,
                        "venue-name",
                        hold.expiresAt().plusDays(1),
                        java.util.Map.of(),
                        java.util.Map.of());
        final Order order =
                new Order(
                        20L,
                        10L,
                        "order-key",
                        "hold-key",
                        hold.expiresAt(),
                        "show-title",
                        hold.expiresAt().plusDays(1),
                        "venue-name");
        when(orderCreator.createPendingOrder(
                        20L, 10L, "hold-key", hold.expiresAt(), seats, saleSnapshot))
                .thenReturn(order);
        when(orderRepository.save(order))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        20L,
                                        10L,
                                        Duration.ofSeconds(600),
                                        hold,
                                        seats,
                                        saleSnapshot))
                .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(orderHoldHistoryRecorder, eventPublisher);
    }

    @Test
    void 주문_저장_메서드는_트랜잭션으로_실행된다() throws NoSuchMethodException {
        assertThat(
                        CreatePendingOrderTransactionService.class
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
}
