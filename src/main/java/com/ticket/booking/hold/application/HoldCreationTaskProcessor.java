package com.ticket.booking.hold.application;

import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.selection.domain.SeatSelectionService;
import com.ticket.booking.domain.PerformanceSeat;
import com.ticket.booking.domain.PerformanceSeatRepository;
import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.SeatStatusEventPublisher;
import com.ticket.booking.support.application.LockKey;
import com.ticket.booking.support.application.LockManager;
import com.ticket.booking.support.application.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldCreationTaskProcessor {

    private final LockManager lockManager;
    private final HoldStore holdStore;
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatStatusEventPublisher seatStatusEventPublisher;

    public void process(final Hold hold) {
        lockManager.withLock(
                LockKey.seats(hold.performanceId(), hold.seatIds()),
                LockOptions.defaults(),
                () -> processLocked(hold)
        );
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
                    hold.performanceId(), performanceSeatIdBySeatId.get(seatId), SeatStatusAction.HELD);
        }
    }

    private Map<Long, Long> resolvePerformanceSeatIds(final Hold hold) {
        return performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(hold.performanceId(), hold.seatIds()).stream()
                .collect(Collectors.toMap(PerformanceSeat::getSeatId, PerformanceSeat::getId));
    }

    private boolean isCurrentHold(final Hold hold) {
        return hold.seatIds().stream()
                .allMatch(seatId -> holdStore.isHeldBy(hold.performanceId(), seatId, hold.holdKey()));
    }
}
