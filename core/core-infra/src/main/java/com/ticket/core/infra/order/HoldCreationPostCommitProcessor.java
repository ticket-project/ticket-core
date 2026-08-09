package com.ticket.core.infra.order;

import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.hold.store.HoldStore;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.core.domain.performanceseat.command.SeatStatusPublisher;
import com.ticket.core.support.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldCreationPostCommitProcessor {

    private final HoldStore holdStore;
    private final SeatSelectionService seatSelectionService;
    private final SeatStatusPublisher seatStatusPublisher;

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#snapshot.seatIds().![#snapshot.performanceId() + ':' + #this]"
    )
    public void process(final HoldSnapshot snapshot) {
        if (!isCurrentHold(snapshot)) {
            log.debug("주문 생성 후처리를 건너뜁니다. hold가 이미 종료되었습니다. holdKey={}", snapshot.holdKey());
            return;
        }

        for (final Long seatId : snapshot.seatIds()) {
            seatSelectionService.deselectIfOwned(snapshot.performanceId(), seatId, snapshot.memberId());
        }
        seatStatusPublisher.publishHeld(snapshot.performanceId(), snapshot.seatIds());
    }

    private boolean isCurrentHold(final HoldSnapshot snapshot) {
        return snapshot.seatIds().stream()
                .allMatch(seatId -> holdStore.isHeldBy(snapshot.performanceId(), seatId, snapshot.holdKey()));
    }
}
