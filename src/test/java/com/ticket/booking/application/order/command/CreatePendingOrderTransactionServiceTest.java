package com.ticket.booking.application.order.command;

import com.ticket.booking.OrderStarted;
import com.ticket.booking.domain.order.command.create.PendingOrderCreationResult;
import com.ticket.booking.domain.order.command.create.HoldAllocation;
import com.ticket.booking.domain.hold.command.HoldHistoryRecorder;
import com.ticket.booking.domain.hold.model.Hold;
import com.ticket.booking.domain.order.model.Order;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeat;
import com.ticket.catalog.PerformanceSaleSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CreatePendingOrderTransactionServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private OrderCreator orderCreator;

    @Mock
    private HoldHistoryRecorder holdHistoryRecorder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CreatePendingOrderTransactionService service;

    @BeforeEach
    void setUp() {
        service = new CreatePendingOrderTransactionService(orderCreator, holdHistoryRecorder, eventPublisher, FIXED_CLOCK);
    }

    @Test
    void 주문과_hold_이력을_같은_트랜잭션에_저장하고_OrderStarted를_발행한다() {
        final Duration holdDuration = Duration.ofSeconds(600);
        final PerformanceSeat seat = mock(PerformanceSeat.class);
        when(seat.getId()).thenReturn(501L);
        final List<PerformanceSeat> seats = List.of(seat);
        final Hold hold = new Hold(
                "hold-key",
                20L,
                10L,
                List.of(7L),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
        final HoldAllocation allocation = new HoldAllocation(hold, seats);
        final Order order = new Order(
                20L,
                10L,
                "order-key",
                "hold-key",
                BigDecimal.valueOf(120000),
                hold.expiresAt(),
                "show-title",
                hold.expiresAt().plusDays(1),
                "venue-name"
        );
        ReflectionTestUtils.setField(order, "id", 55L);
        final PerformanceSaleSnapshot saleSnapshot = new PerformanceSaleSnapshot(
                10L, 1L, "show-title", 1L, "venue-name", hold.expiresAt().plusDays(1),
                java.util.Map.of(), java.util.Map.of());

        when(orderCreator.createPendingOrder(20L, 10L, "hold-key", hold.expiresAt(), seats, saleSnapshot))
                .thenReturn(order);

        final PendingOrderCreationResult result = service.create(20L, 10L, holdDuration, allocation, saleSnapshot);

        assertThat(result.order()).isSameAs(order);
        final InOrder inOrder = inOrder(orderCreator, holdHistoryRecorder, eventPublisher);
        inOrder.verify(orderCreator).createPendingOrder(20L, 10L, "hold-key", hold.expiresAt(), seats, saleSnapshot);
        inOrder.verify(holdHistoryRecorder).recordCreated(
                20L,
                10L,
                "hold-key",
                hold.expiresAt().minusSeconds(600),
                hold.expiresAt(),
                seats
        );
        final ArgumentCaptor<OrderStarted> captor = ArgumentCaptor.forClass(OrderStarted.class);
        inOrder.verify(eventPublisher).publishEvent(captor.capture());
        final OrderStarted event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(55L);
        assertThat(event.memberId()).isEqualTo(20L);
        assertThat(event.holdKey()).isEqualTo("hold-key");
        assertThat(event.performanceSeatIds()).isEqualTo(Set.of(501L));
        assertThat(event.schemaVersion()).isEqualTo(OrderStarted.SCHEMA_VERSION);
        assertThat(event.occurredAt())
                .isEqualTo(hold.expiresAt().minusSeconds(600).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    }

    @Test
    void 주문_저장_메서드는_트랜잭션으로_실행된다() throws NoSuchMethodException {
        assertThat(CreatePendingOrderTransactionService.class
                .getDeclaredMethod("create", Long.class, Long.class, Duration.class, HoldAllocation.class, PerformanceSaleSnapshot.class)
                .isAnnotationPresent(Transactional.class))
                .isTrue();
    }
}
