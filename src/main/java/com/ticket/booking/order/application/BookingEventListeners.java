package com.ticket.booking.order.application;

import com.ticket.booking.hold.application.HoldCreationTaskProcessor;
import com.ticket.booking.hold.application.HoldReleaseProgressRecorder;
import com.ticket.booking.hold.application.HoldReleaseTask;
import com.ticket.booking.hold.application.HoldReleaseTaskProcessor;
import com.ticket.booking.OrderStarted;
import com.ticket.booking.OrderTerminated;

import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.order.domain.Order;
import com.ticket.booking.order.domain.OrderSeat;
import com.ticket.booking.order.domain.OrderRepository;
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
 * {@code com.ticket.shared.config.EventPublicationMaintenance}가 재시도한다.
 *
 * <p>event payload의 스냅샷을 그대로 믿지 않고 {@code orderId}로 현재 저장된 order를 다시 읽어
 * 처리한다 — 좌석은 Order aggregate가 직접 들고 있어 함께 따라온다. hold 생성
 * 후처리({@link HoldCreationTaskProcessor})와 hold 해제 후처리
 * ({@link HoldReleaseTaskProcessor})는 Task 7 이전부터 있던 멱등 로직을 그대로 재사용한다. *
 * <p><b>listener id는 옛 package 경로를 그대로 유지한다.</b> Spring의 기본 listener id는
 * {@code <선언 클래스 FQCN>.<메서드>(<파라미터 타입>)}(Spring Framework
 * {@code ApplicationListenerMethodAdapter#getDefaultListenerId})라, 이 클래스가
 * {@code com.ticket.booking.application}에서 {@code com.ticket.booking.order.application}으로
 * 옮겨지면 id가 바뀐다. id가 바뀌면 EVENT_PUBLICATION에 남아 있는 미완료 publication이 어떤
 * listener의 것인지 매칭되지 않아 재처리도 완료 처리도 되지 않는다. 그래서 {@code id}를 옛
 * 값으로 명시해 고정한다 — 아래 문자열의 {@code booking.application}은 현재 package가 아니라
 * <b>호환성 식별자</b>이므로 package 경로 일괄 치환 대상에서 제외한다.
 * {@code BookingEventListenerIdContractTest}가 이 값을 고정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookingEventListeners {

    /**
     * 이 클래스가 {@code com.ticket.booking.application}에 있던 시절의 기본 listener id다.
     * 저장된 publication과 맞추기 위한 호환성 값이라 현재 package로 고치지 않는다.
     */
    static final String ORDER_STARTED_LISTENER_ID =
            "com.ticket.booking.application.BookingEventListeners.on(com.ticket.booking.OrderStarted)";

    /** @see #ORDER_STARTED_LISTENER_ID */
    static final String ORDER_TERMINATED_LISTENER_ID =
            "com.ticket.booking.application.BookingEventListeners.on(com.ticket.booking.OrderTerminated)";

    private final OrderRepository orderRepository;
    private final HoldCreationTaskProcessor holdCreationTaskProcessor;
    private final HoldReleaseTaskProcessor holdReleaseTaskProcessor;
    private final HoldReleaseProgressRecorder holdReleaseProgressRecorder;
    private final Clock clock;

    /** @see #ORDER_STARTED_LISTENER_ID */
    @ApplicationModuleListener(id = ORDER_STARTED_LISTENER_ID)
    void on(final OrderStarted event) {
        final Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.debug("주문 생성 후처리를 건너뜁니다. 주문을 찾을 수 없습니다. orderId={}", event.orderId());
            return;
        }
        final List<Long> seatIds = seatIdsOf(order);
        final Hold hold = new Hold(event.holdKey(), event.memberId(), order.getPerformanceId(), seatIds, order.getExpiresAt());
        holdCreationTaskProcessor.process(hold);
    }

    /** @see #ORDER_TERMINATED_LISTENER_ID */
    @ApplicationModuleListener(id = ORDER_TERMINATED_LISTENER_ID)
    void on(final OrderTerminated event) {
        final Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.debug("hold 해제 후처리를 건너뜁니다. 주문을 찾을 수 없습니다. orderId={}", event.orderId());
            return;
        }
        final List<Long> seatIds = seatIdsOf(order);
        final boolean alreadyReleased = holdReleaseProgressRecorder.isReleased(event.eventId());
        final HoldReleaseTask task = new HoldReleaseTask(order.getPerformanceId(), event.holdKey(), seatIds, alreadyReleased);
        holdReleaseTaskProcessor.process(event.eventId(), task, LocalDateTime.now(clock));
    }

    private List<Long> seatIdsOf(final Order order) {
        return order.getOrderSeats().stream()
                .map(OrderSeat::getSeatId)
                .toList();
    }
}
