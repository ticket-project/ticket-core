package com.ticket.core.domain.order.command.create;

import com.ticket.core.domain.hold.command.HoldHistoryRecorder;
import com.ticket.core.domain.hold.event.HoldCreatedEvent;
import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.order.model.Order;
import com.ticket.core.domain.performanceseat.model.PerformanceSeat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class CreatePendingOrderTxServiceTest {

    @Mock
    private OrderCreator orderCreator;

    @Mock
    private HoldHistoryRecorder holdHistoryRecorder;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private CreatePendingOrderTxService service;

    @BeforeEach
    void setUp() {
        service = new CreatePendingOrderTxService(orderCreator, holdHistoryRecorder, applicationEventPublisher);
    }

    @Test
    void 주문과_hold_이력을_저장하고_이벤트를_발행한다() {
        final Duration holdDuration = Duration.ofSeconds(600);
        final List<PerformanceSeat> seats = List.of(mock(PerformanceSeat.class));
        final HoldSnapshot snapshot = new HoldSnapshot(
                "hold-key",
                20L,
                10L,
                List.of(7L),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
        final HoldAllocation allocation = new HoldAllocation(snapshot, seats);
        final Order order = new Order(
                20L,
                10L,
                "order-key",
                "hold-key",
                BigDecimal.valueOf(120000),
                snapshot.expiresAt()
        );

        when(orderCreator.createPendingOrder(20L, 10L, "hold-key", snapshot.expiresAt(), seats))
                .thenReturn(order);

        final Order result = service.create(20L, 10L, holdDuration, allocation);

        assertThat(result).isSameAs(order);
        final InOrder inOrder = inOrder(orderCreator, holdHistoryRecorder, applicationEventPublisher);
        inOrder.verify(orderCreator).createPendingOrder(20L, 10L, "hold-key", snapshot.expiresAt(), seats);
        inOrder.verify(holdHistoryRecorder).recordCreated(
                20L,
                10L,
                "hold-key",
                snapshot.expiresAt().minusSeconds(600),
                snapshot.expiresAt(),
                seats
        );
        inOrder.verify(applicationEventPublisher).publishEvent(any(HoldCreatedEvent.class));
    }

    @Test
    void 이벤트_발행_실패를_호출자에게_전파한다() {
        final Duration holdDuration = Duration.ofSeconds(600);
        final HoldSnapshot snapshot = new HoldSnapshot(
                "hold-key",
                20L,
                10L,
                List.of(7L),
                LocalDateTime.of(2026, 3, 15, 12, 0)
        );
        final HoldAllocation allocation = new HoldAllocation(snapshot, List.of(mock(PerformanceSeat.class)));
        final Order order = new Order(
                20L,
                10L,
                "order-key",
                "hold-key",
                BigDecimal.valueOf(120000),
                snapshot.expiresAt()
        );

        when(orderCreator.createPendingOrder(
                20L,
                10L,
                "hold-key",
                snapshot.expiresAt(),
                allocation.performanceSeats()
        )).thenReturn(order);
        doThrow(new RuntimeException("event failed"))
                .when(applicationEventPublisher).publishEvent(any(HoldCreatedEvent.class));

        assertThatThrownBy(() -> service.create(20L, 10L, holdDuration, allocation))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("event failed");
    }

    @Test
    void 주문_저장_메서드는_트랜잭션으로_실행된다() throws NoSuchMethodException {
        assertThat(CreatePendingOrderTxService.class
                .getDeclaredMethod("create", Long.class, Long.class, Duration.class, HoldAllocation.class)
                .isAnnotationPresent(Transactional.class))
                .isTrue();
    }
}
