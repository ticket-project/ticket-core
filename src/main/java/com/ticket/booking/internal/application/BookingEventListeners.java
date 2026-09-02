package com.ticket.booking.internal.application;

import com.ticket.booking.OrderStarted;
import com.ticket.booking.OrderTerminated;
import com.ticket.booking.internal.application.event.HoldReleaseProgressRecorder;
import com.ticket.booking.internal.application.order.command.HoldCreationTaskProcessor;
import com.ticket.booking.internal.application.order.command.HoldReleaseTask;
import com.ticket.booking.internal.application.order.command.HoldReleaseTaskProcessor;
import com.ticket.booking.internal.domain.hold.model.Hold;
import com.ticket.booking.internal.domain.order.model.Order;
import com.ticket.booking.internal.domain.order.model.OrderSeat;
import com.ticket.booking.internal.domain.order.repository.OrderRepository;
import com.ticket.booking.internal.domain.order.repository.OrderSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 시작·종료 후속 처리(selection 정리, Redis hold 해제, WebSocket 발행)를 맡는 listener다.
 *
 * <p>Spring Modulith의 JPA event publication registry가 커밋 뒤 최소 한 번 전달을 보장한다. 여기서
 * 실패를 catch-and-log로 삼키지 않고 그대로 던져 registry가 FAILED로 기록하게 하고,
 * {@code com.ticket.configuration.EventPublicationMaintenance}가 재시도한다.
 *
 * <p>event payload의 스냅샷을 그대로 믿지 않고 {@code orderId}로 현재 저장된 order·orderSeat를
 * 다시 읽어 처리한다. hold 생성 후처리({@link HoldCreationTaskProcessor})와 hold 해제 후처리
 * ({@link HoldReleaseTaskProcessor})는 Task 7 이전부터 있던 멱등 로직을 그대로 재사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookingEventListeners {

    private final OrderRepository orderRepository;
    private final OrderSeatRepository orderSeatRepository;
    private final HoldCreationTaskProcessor holdCreationTaskProcessor;
    private final HoldReleaseTaskProcessor holdReleaseTaskProcessor;
    private final HoldReleaseProgressRecorder holdReleaseProgressRecorder;
    private final Clock clock;

    @ApplicationModuleListener
    void on(final OrderStarted event) {
        final Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.debug("주문 생성 후처리를 건너뜁니다. 주문을 찾을 수 없습니다. orderId={}", event.orderId());
            return;
        }
        final List<Long> seatIds = seatIdsOf(event.orderId());
        final Hold hold = new Hold(event.holdKey(), event.memberId(), order.getPerformanceId(), seatIds, order.getExpiresAt());
        holdCreationTaskProcessor.process(hold);
    }

    @ApplicationModuleListener
    void on(final OrderTerminated event) {
        final Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.debug("hold 해제 후처리를 건너뜁니다. 주문을 찾을 수 없습니다. orderId={}", event.orderId());
            return;
        }
        final List<Long> seatIds = seatIdsOf(event.orderId());
        final boolean alreadyReleased = holdReleaseProgressRecorder.isReleased(event.eventId());
        final HoldReleaseTask task = new HoldReleaseTask(order.getPerformanceId(), event.holdKey(), seatIds, alreadyReleased);
        holdReleaseTaskProcessor.process(event.eventId(), task, LocalDateTime.now(clock));
    }

    private List<Long> seatIdsOf(final Long orderId) {
        return orderSeatRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
                .map(OrderSeat::getSeatId)
                .toList();
    }
}
