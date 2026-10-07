package com.ticket.booking.hold.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HoldRegistry {
    private final HoldStore holdStore;

    /** 좌석을 선점한다. 좌석 단위 상호 배제는 호출하는 유스케이스가 락으로 보장한다. */
    public Hold createHold(
            final Long memberId,
            final Long performanceId,
            final List<Long> requestedSeatIds,
            final Duration ttl,
            final LocalDateTime now) {
        final Hold hold = Hold.create(generateHoldKey(), memberId, performanceId, requestedSeatIds, now, ttl);

        if (!holdStore.saveIfAbsent(hold, ttl)) {
            throw new BookingException(BookingErrorCode.E6000);
        }
        return hold;
    }

    /** 선점을 해제한다. 좌석 단위 상호 배제는 호출하는 유스케이스가 락으로 보장한다. */
    public List<Long> release(final Long performanceId, final String holdKey, final List<Long> seatIds) {
        return holdStore.release(performanceId, holdKey, seatIds);
    }

    public Set<Long> getHoldingSeatIds(final Long performanceId) {
        return holdStore.getHoldingSeatIds(performanceId);
    }

    public boolean isHeld(final Long performanceId, final Long seatId) {
        return holdStore.isHeld(performanceId, seatId);
    }

    private String generateHoldKey() {
        return "HOLD-" + UUID.randomUUID().toString().replace("-", "");
    }
}
