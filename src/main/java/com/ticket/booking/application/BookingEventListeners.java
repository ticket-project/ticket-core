package com.ticket.booking.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import com.ticket.booking.OrderStarted;
import com.ticket.booking.OrderTerminated;
import com.ticket.booking.application.port.HoldReleaseProgressRecorder;
import com.ticket.booking.domain.hold.Hold;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 시작·종료 후속 처리(selection 정리, Redis hold 해제, WebSocket 발행)를 맡는 listener다.
 *
 * <p>Spring Modulith의 JPA event publication registry가 커밋 뒤 최소 한 번 전달을 보장한다. 여기서 실패를 catch-and-log로
 * 삼키지 않고 그대로 던져 registry가 FAILED로 기록하게 하고, {@code
 * com.ticket.shared.infrastructure.EventPublicationMaintenance}가 재시도한다.
 *
 * <p>event payload의 스냅샷을 그대로 믿지 않고 {@code orderId}로 현재 저장된 order를 다시 읽어 처리한다 — 좌석은 Order aggregate가
 * 직접 들고 있어 함께 따라온다. hold 생성 후처리({@link HoldCreationCoordinator})와 hold 해제 후처리 ({@link
 * HoldReleaseCoordinator})는 기존 멱등 로직을 그대로 재사용한다.
 *
 * <p><b>listener 자체는 DB 트랜잭션을 열지 않는다({@code propagation = NOT_SUPPORTED}).</b> 기본값인 {@code
 * REQUIRES_NEW}에서는 Redis 락 대기·Redis 접근·WebSocket 발행이 모두 하나의 booking 트랜잭션 안에서 실행돼 외부 지연이 그대로
 * connection 점유가 됐다. 되돌림 의미는 더 나빴다 — Redis 해제 뒤 남긴 완료 기록({@code HoldReleaseProgress})이 뒤이은
 * WebSocket 실패로 함께 롤백돼, 재시도에서 Redis 해제를 다시 수행했다. 지금은 필요한 DB 데이터를 {@link OrderHoldSnapshotReader}의
 * 짧은 읽기 트랜잭션에서 값으로 완성한 뒤 그 밖에서 외부 작업을 하고, 완료 기록은 자기 트랜잭션에서 곧바로 커밋된다.
 *
 * <p>트랜잭션을 열지 않아도 publication 계약은 그대로다 — 발행·완료·실패 기록은 Modulith registry가 자기 트랜잭션에서 수행하고, 여기서 던진 예외는
 * FAILED로 남아 재제출된다. 이 listener는 예외를 삼키지 않는다.
 *
 * <p><b>listener id는 옛 package 경로를 그대로 유지한다.</b> Spring의 기본 listener id는 {@code <선언 클래스
 * FQCN>.<메서드>(<파라미터 타입>)}(Spring Framework {@code
 * ApplicationListenerMethodAdapter#getDefaultListenerId})라, 이 클래스가 {@code
 * com.ticket.booking.application}에서 {@code com.ticket.booking.order.application}으로 옮겨졌다가 다시 돌아왔다.
 * 어느 쪽이든 package가 바뀌면 id가 바뀐다. id가 바뀌면 EVENT_PUBLICATION에 남아 있는 미완료 publication이 어떤 listener의 것인지
 * 매칭되지 않아 재처리도 완료 처리도 되지 않는다. 그래서 {@code id}를 옛 값으로 명시해 고정한다 — 아래 문자열의 {@code booking.application}은
 * 현재 package가 아니라 <b>호환성 식별자</b>이므로 package 경로 일괄 치환 대상에서 제외한다. {@code
 * BookingEventListenerIdContractTest}가 이 값을 고정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookingEventListeners {
    /**
     * 이 클래스가 {@code com.ticket.booking.application}에 있던 시절의 기본 listener id다. 저장된 publication과 맞추기
     * 위한 호환성 값이라 현재 package로 고치지 않는다.
     */
    static final String ORDER_STARTED_LISTENER_ID =
            "com.ticket.booking.application.BookingEventListeners.on(com.ticket.booking.OrderStarted)";

    /**
     * @see #ORDER_STARTED_LISTENER_ID
     */
    static final String ORDER_TERMINATED_LISTENER_ID =
            "com.ticket.booking.application.BookingEventListeners.on(com.ticket.booking.OrderTerminated)";

    private final OrderHoldSnapshotReader orderHoldSnapshotReader;
    private final HoldCreationCoordinator holdCreationCoordinator;
    private final HoldReleaseCoordinator holdReleaseCoordinator;
    private final HoldReleaseProgressRecorder holdReleaseProgressRecorder;
    private final Clock clock;

    /**
     * @see #ORDER_STARTED_LISTENER_ID
     */
    @ApplicationModuleListener(
            id = ORDER_STARTED_LISTENER_ID,
            propagation = Propagation.NOT_SUPPORTED)
    void on(final OrderStarted event) {
        final OrderHoldSnapshot snapshot =
                orderHoldSnapshotReader.read(event.orderId()).orElse(null);
        if (snapshot == null) {
            log.debug("주문 생성 후처리를 건너뜁니다. 주문을 찾을 수 없습니다. orderId={}", event.orderId());
            return;
        }
        final Hold hold =
                new Hold(
                        event.holdKey(),
                        event.memberId(),
                        snapshot.performanceId(),
                        snapshot.seatIds(),
                        snapshot.expiresAt());
        holdCreationCoordinator.clearSelectionsAndPublishHeld(hold);
    }

    /**
     * @see #ORDER_TERMINATED_LISTENER_ID
     */
    @ApplicationModuleListener(
            id = ORDER_TERMINATED_LISTENER_ID,
            propagation = Propagation.NOT_SUPPORTED)
    void on(final OrderTerminated event) {
        final OrderHoldSnapshot snapshot =
                orderHoldSnapshotReader.read(event.orderId()).orElse(null);
        if (snapshot == null) {
            log.debug("hold 해제 후처리를 건너뜁니다. 주문을 찾을 수 없습니다. orderId={}", event.orderId());
            return;
        }
        final boolean alreadyReleased = holdReleaseProgressRecorder.isReleased(event.eventId());
        final HoldReleaseTask task =
                new HoldReleaseTask(
                        snapshot.performanceId(),
                        event.holdKey(),
                        snapshot.seatIds(),
                        alreadyReleased);
        holdReleaseCoordinator.releaseAndPublish(event.eventId(), task, LocalDateTime.now(clock));
    }
}
