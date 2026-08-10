package com.ticket.core.domain.hold.command;

import com.ticket.core.support.lock.DistributedLock;
import com.ticket.core.domain.hold.model.HoldSnapshot;
import com.ticket.core.domain.hold.store.HoldStore;
import com.ticket.core.domain.order.command.create.RequestedSeatIds;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
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

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#requestedSeatIds.toList().![#performanceId + ':' + #this]"
    )
    public HoldSnapshot createHold(
            final Long memberId,
            final Long performanceId,
            final RequestedSeatIds requestedSeatIds,
            final Duration ttl,
            final LocalDateTime now
    ) {
        final List<Long> seatIds = requestedSeatIds.toList();
        final String holdKey = holdKeyGenerator.generate();
        final LocalDateTime expiresAt = now.plus(ttl);
        final HoldSnapshot snapshot = new HoldSnapshot(holdKey, memberId, performanceId, seatIds, expiresAt);

        ensureSeatsNotHeld(performanceId, seatIds);
        saveHold(snapshot, ttl);
        return snapshot;
    }

    @DistributedLock(
            prefix = "hold",
            dynamicKey = "#seatIds.![#performanceId + ':' + #this]"
    )
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
                throw new CoreException(ErrorType.SEAT_ALREADY_HOLD);
            }
        }
    }

    private void saveHold(final HoldSnapshot snapshot, final Duration ttl) {
        holdStore.save(snapshot, ttl);
    }
}
