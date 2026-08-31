package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.Hold;
import com.ticket.core.domain.hold.store.HoldStore;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.app.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.core.app.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.core.app.lock.LockKey;
import com.ticket.core.app.lock.LockManager;
import com.ticket.core.app.lock.LockOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldCreationPostCommitProcessor {

    private final LockManager lockManager;
    private final HoldStore holdStore;
    private final SeatSelectionService seatSelectionService;
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
        for (final Long seatId : hold.seatIds()) {
            seatStatusEventPublisher.publish(hold.performanceId(), seatId, SeatStatusAction.HELD);
        }
    }

    private boolean isCurrentHold(final Hold hold) {
        return hold.seatIds().stream()
                .allMatch(seatId -> holdStore.isHeldBy(hold.performanceId(), seatId, hold.holdKey()));
    }
}
