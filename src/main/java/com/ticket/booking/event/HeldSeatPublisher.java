package com.ticket.booking.event;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.ticket.booking.concurrency.DistributedLock;
import com.ticket.booking.concurrency.LockKey;
import com.ticket.booking.concurrency.LockOptions;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.port.SeatStatusEventPublisher;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.selection.usecase.SeatSelectionWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문이 만들어진 뒤 그 좌석들에 남은 선택 상태를 정리하고, 좌석이 선점됐다는 사실을 좌석 상태 이벤트로 알린다.
 *
 * <p>선점 자체는 이 시점에 이미 Redis에 기록돼 있다. 여기서 하는 일은 선점자의 좌석 선택을 거두고 HELD를 발행하는 것이다.
 *
 * <p><b>좌석 락 안에서 상태 변경과 발행을 함께 한다</b> — {@link SeatSelectionWriter}와 같은 이유다. 선점 확정과 선택 정리, 그리고 그것을 알리는 발행이 같은 임계 구역 안에
 * 있어야 서버가 내보내는 순서가 상태 변화 순서와 같아진다.
 *
 * <p>DB 읽기는 listener의 snapshot reader에서 끝낸다. 여기서는 좌석 락 안에서 현재 hold를 확인하고 Redis·WebSocket 작업만 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeldSeatPublisher {
    private final DistributedLock distributedLock;
    private final HoldStore holdStore;
    private final SeatSelectionService seatSelectionService;
    private final SeatStatusEventPublisher seatStatusEventPublisher;

    public void clearSelectionsAndPublishHeld(final Hold hold, final Map<Long, Long> performanceSeatIdBySeatId) {
        distributedLock.withLock(
                LockKey.seats(hold.performanceId(), hold.seatIds()),
                LockOptions.defaults(),
                () -> clearSelectionsAndPublishHeldLocked(hold, performanceSeatIdBySeatId));
    }

    private void clearSelectionsAndPublishHeldLocked(final Hold hold, final Map<Long, Long> performanceSeatIdBySeatId) {
        if (!isCurrentHold(hold)) {
            log.debug("주문 생성 후처리를 건너뜁니다. hold가 이미 종료되었습니다. holdKey={}", hold.holdKey());
            return;
        }

        for (final Long seatId : hold.seatIds()) {
            seatSelectionService.deselectIfOwned(hold.performanceId(), seatId, hold.memberId());
        }
        for (final Long seatId : hold.seatIds()) {
            seatStatusEventPublisher.publish(
                    hold.performanceId(), performanceSeatIdBySeatId.get(seatId), seatId, SeatStatusAction.HELD);
        }
    }

    private boolean isCurrentHold(final Hold hold) {
        return hold.seatIds().stream()
                .allMatch(seatId -> holdStore.isHeldBy(hold.performanceId(), seatId, hold.holdKey()));
    }
}
