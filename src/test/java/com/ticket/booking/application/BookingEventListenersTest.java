package com.ticket.booking.application;

import com.ticket.booking.hold.application.HoldCreationTaskProcessor;
import com.ticket.booking.hold.application.HoldReleaseProgressRecorder;
import com.ticket.booking.hold.application.HoldReleaseTask;
import com.ticket.booking.hold.application.HoldReleaseTaskProcessor;
import com.ticket.booking.OrderStarted;
import com.ticket.booking.OrderTerminated;

import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.domain.Order;
import com.ticket.booking.domain.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Task 8 Step 5: listener 멱등성과 stale-event 방어를 고정한다.
 *
 * <p>{@link BookingEventListeners}는 event payload를 그대로 믿지 않고 {@code orderId}로 현재
 * 저장된 order·orderSeat를 다시 읽는다. hold 생성·해제 자체의 멱등 로직은
 * {@code HoldCreationTaskProcessorTest}/{@code HoldReleaseTaskProcessorTest}가 이미 고정하므로,
 * 여기서는 listener가 그 로직에 올바른 입력(특히 {@code holdReleased} 플래그)을 넘기는지와
 * 존재하지 않는 주문에 대해 아무 부수효과도 일으키지 않는지를 본다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class BookingEventListenersTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HoldCreationTaskProcessor holdCreationTaskProcessor;

    @Mock
    private HoldReleaseTaskProcessor holdReleaseTaskProcessor;

    @Mock
    private HoldReleaseProgressRecorder holdReleaseProgressRecorder;

    private BookingEventListeners listeners;

    @BeforeEach
    void setUp() {
        listeners = new BookingEventListeners(
                orderRepository,
                holdCreationTaskProcessor,
                holdReleaseTaskProcessor,
                holdReleaseProgressRecorder,
                FIXED_CLOCK
        );
    }

    @Test
    void OrderStarted_주문이_없으면_아무_후처리도_하지_않는다() {
        final OrderStarted event = orderStarted(10L, "hold-key");
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        listeners.on(event);

        verifyNoInteractions(holdCreationTaskProcessor);
    }

    @Test
    void OrderStarted는_현재_DB_상태로_Hold를_재구성해_생성_프로세서에_넘긴다() {
        final OrderStarted event = orderStarted(10L, "hold-key");
        final Order order = order(10L, 200L, "hold-key", LocalDateTime.of(2026, 3, 15, 10, 10));
        addOrderSeat(order, 501L, 42L);
        addOrderSeat(order, 502L, 43L);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        listeners.on(event);

        final Hold expectedHold = new Hold("hold-key", 20L, 200L, List.of(42L, 43L), order.getExpiresAt());
        verify(holdCreationTaskProcessor).process(expectedHold);
    }

    @Test
    void OrderTerminated_주문이_없으면_아무_후처리도_하지_않는다() {
        final OrderTerminated event = orderTerminated(11L, "hold-key");
        when(orderRepository.findById(11L)).thenReturn(Optional.empty());

        listeners.on(event);

        verifyNoInteractions(holdReleaseTaskProcessor, holdReleaseProgressRecorder);
    }

    @Test
    void OrderTerminated는_최초_전달이면_holdReleased_false로_해제_프로세서를_부른다() {
        final OrderTerminated event = orderTerminated(11L, "hold-key");
        final Order order = order(11L, 200L, "hold-key", LocalDateTime.of(2026, 3, 15, 10, 10));
        addOrderSeat(order, 501L, 42L);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(holdReleaseProgressRecorder.isReleased(event.eventId())).thenReturn(false);

        listeners.on(event);

        final ArgumentCaptor<HoldReleaseTask> captor = ArgumentCaptor.forClass(HoldReleaseTask.class);
        verify(holdReleaseTaskProcessor).process(org.mockito.ArgumentMatchers.eq(event.eventId()), captor.capture(), org.mockito.ArgumentMatchers.eq(LocalDateTime.now(FIXED_CLOCK)));
        assertThat(captor.getValue()).isEqualTo(new HoldReleaseTask(200L, "hold-key", List.of(42L), false));
    }

    /**
     * 같은 eventId가 재전달되면(at-least-once) listener는 매번 DB를 다시 읽지만, Redis 해제
     * 자체는 {@link HoldReleaseProgressRecorder}에 남은 진행 상태로 건너뛴다. 이 재확인이 바로
     * "예전 event가 새 hold/selection을 지우지 못하게" 하는 지점이다 — 재전달에서
     * {@code holdReleased=true}가 넘어가야 {@link HoldReleaseTaskProcessor}가 Redis release를
     * 반복하지 않는다.
     */
    @Test
    void OrderTerminated_재전달이면_holdReleased_true로_넘겨_Redis_해제를_반복하지_않는다() {
        final OrderTerminated event = orderTerminated(11L, "hold-key");
        final Order order = order(11L, 200L, "hold-key", LocalDateTime.of(2026, 3, 15, 10, 10));
        addOrderSeat(order, 501L, 42L);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));
        when(holdReleaseProgressRecorder.isReleased(event.eventId())).thenReturn(true);

        listeners.on(event);
        listeners.on(event);

        final ArgumentCaptor<HoldReleaseTask> captor = ArgumentCaptor.forClass(HoldReleaseTask.class);
        verify(holdReleaseTaskProcessor, org.mockito.Mockito.times(2))
                .process(org.mockito.ArgumentMatchers.eq(event.eventId()), captor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(captor.getAllValues())
                .allSatisfy(task -> assertThat(task.holdReleased()).isTrue());
    }

    private OrderStarted orderStarted(final long orderId, final String holdKey) {
        return new OrderStarted(UUID.randomUUID(), OrderStarted.SCHEMA_VERSION, orderId, 20L, holdKey, Set.of(501L), Instant.now());
    }

    private OrderTerminated orderTerminated(final long orderId, final String holdKey) {
        return new OrderTerminated(
                UUID.randomUUID(), OrderTerminated.SCHEMA_VERSION, orderId, 20L, holdKey, Set.of(501L), "CANCELED", Instant.now());
    }

    private Order order(final Long id, final Long performanceId, final String holdKey, final LocalDateTime expiresAt) {
        final Order order = new Order(
                20L, performanceId, "order-" + id, holdKey, BigDecimal.TEN, expiresAt,
                "show-title", expiresAt.minusDays(1), "venue-name");
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private void addOrderSeat(final Order order, final Long performanceSeatId, final Long seatId) {
        order.addOrderSeat(performanceSeatId, seatId, BigDecimal.TEN, "R", "R석", "1F 가구역 A열 1번");
    }
}
