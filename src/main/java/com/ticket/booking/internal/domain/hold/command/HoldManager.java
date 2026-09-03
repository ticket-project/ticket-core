package com.ticket.booking.internal.domain.hold.command;

import com.ticket.booking.internal.domain.hold.model.Hold;
import com.ticket.booking.internal.domain.hold.store.HoldStore;
import com.ticket.booking.internal.domain.order.command.create.RequestedSeatIds;
import com.ticket.booking.internal.exception.SeatAlreadyHoldException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class HoldManager {

    private final HoldStore holdStore;
    private final HoldKeyGenerator holdKeyGenerator;

    /**
     * 좌석을 선점한다. 좌석 단위 상호 배제는 호출하는 유스케이스가 락으로 보장한다.
     */
    public Hold createHold(
            final Long memberId,
            final Long performanceId,
            final RequestedSeatIds requestedSeatIds,
            final Duration ttl,
            final LocalDateTime now
    ) {
        final List<Long> seatIds = requestedSeatIds.toList();
        final Hold hold = Hold.create(holdKeyGenerator.generate(), memberId, performanceId, seatIds, now, ttl);

        ensureSeatsNotHeld(performanceId, seatIds);
        holdStore.save(hold, ttl);
        return hold;
    }

    /**
     * 선점을 해제한다. 좌석 단위 상호 배제는 호출하는 유스케이스가 락으로 보장한다.
     */
    public List<Long> release(final Long performanceId, final String holdKey, final List<Long> seatIds) {
        final List<Long> normalizedSeatIds = seatIds.stream().distinct().sorted().toList();
        return holdStore.release(performanceId, holdKey, normalizedSeatIds);
    }

    public Set<Long> getHoldingSeatIds(final Long performanceId) {
        return holdStore.getHoldingSeatIds(performanceId);
    }

    public boolean isHeld(final Long performanceId, final Long seatId) {
        return holdStore.isHeld(performanceId, seatId);
    }

    private void ensureSeatsNotHeld(final Long performanceId, final List<Long> seatIds) {
        for (final Long seatId : seatIds) {
            if (isHeld(performanceId, seatId)) {
                throw new SeatAlreadyHoldException();
            }
        }
    }
}
