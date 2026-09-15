package com.ticket.booking.application;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.domain.hold.Hold;
import com.ticket.booking.domain.hold.HoldStore;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.domain.selection.SeatSelectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문이 만들어진 뒤 좌석 선점을 실제로 확정하고, 그 결과를 좌석 상태 이벤트로 알린다.
 *
 * <p><b>좌석 락 안에서 상태 변경과 발행을 함께 한다</b> — {@link SeatSelectionCoordinator}와 같은 이유다. 선점 확정과 선택 정리, 그리고
 * 그것을 알리는 발행이 같은 임계 구역 안에 있어야 서버가 내보내는 순서가 상태 변화 순서와 같아진다.
 *
 * <p>이 후속 처리는 트랜잭션 없이 실행되는 listener에서 불린다({@link BookingEventListeners}). 그래서 여기서는 DB를 조회만 하고, 멱등
 * 판정(이미 끝난 hold인가)을 락 안에서 먼저 한다 — 이벤트가 재전달돼도 두 번 확정되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldCreationCoordinator {
    private final LockManager lockManager;
    private final HoldStore holdStore;
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatStatusEventPublisher seatStatusEventPublisher;

    public void process(final Hold hold) {
        lockManager.withLock(
                LockKey.seats(hold.performanceId(), hold.seatIds()),
                LockOptions.defaults(),
                () -> processLocked(hold));
    }

    private void processLocked(final Hold hold) {
        if (!isCurrentHold(hold)) {
            log.debug("주문 생성 후처리를 건너뜁니다. hold가 이미 종료되었습니다. holdKey={}", hold.holdKey());
            return;
        }

        for (final Long seatId : hold.seatIds()) {
            seatSelectionService.deselectIfOwned(hold.performanceId(), seatId, hold.memberId());
        }
        final Map<Long, Long> performanceSeatIdBySeatId = resolvePerformanceSeatIds(hold);
        for (final Long seatId : hold.seatIds()) {
            seatStatusEventPublisher.publish(
                    hold.performanceId(),
                    performanceSeatIdBySeatId.get(seatId),
                    seatId,
                    SeatStatusAction.HELD);
        }
    }

    private Map<Long, Long> resolvePerformanceSeatIds(final Hold hold) {
        return performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(hold.performanceId(), hold.seatIds())
                .stream()
                .collect(Collectors.toMap(PerformanceSeat::getSeatId, PerformanceSeat::getId));
    }

    private boolean isCurrentHold(final Hold hold) {
        return hold.seatIds().stream()
                .allMatch(
                        seatId -> holdStore.isHeldBy(hold.performanceId(), seatId, hold.holdKey()));
    }
}
