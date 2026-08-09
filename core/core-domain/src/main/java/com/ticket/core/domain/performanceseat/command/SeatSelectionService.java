package com.ticket.core.domain.performanceseat.command;

import com.ticket.core.domain.performanceseat.store.SeatSelectionStore;
import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionService {

    private static final Duration SELECT_TTL = Duration.ofMinutes(5);

    private final SeatSelectionStore seatSelectionStore;

    public void select(final Long performanceId, final Long seatId, final Long memberId) {
        final String memberKey = memberKeyOf(memberId);
        final boolean locked = seatSelectionStore.selectIfAbsent(performanceId, seatId, memberKey, SELECT_TTL);
        if (!locked) {
            log.warn("좌석 선택에 실패했습니다. performanceId={}, seatId={}, memberId={}", performanceId, seatId, memberId);
            throw new CoreException(ErrorType.SEAT_ALREADY_SELECTED);
        }
        log.debug("좌석 선택에 성공했습니다. performanceId={}, seatId={}, memberId={}", performanceId, seatId, memberId);
    }

    public void deselect(final Long performanceId, final Long seatId, final Long memberId) {
        final String memberKey = memberKeyOf(memberId);
        final String holder = seatSelectionStore.getHolder(performanceId, seatId);
        if (holder == null) {
            log.debug("좌석 선택 해제를 건너뜁니다. 이미 선택 정보가 없습니다. performanceId={}, seatId={}", performanceId, seatId);
            return;
        }
        validateOwner(performanceId, seatId, memberId, memberKey, holder);
        releaseSeat(performanceId, seatId, memberId, memberKey);
    }

    public DeselectedSeatIds deselectAll(final Long performanceId, final Long memberId) {
        final String memberKey = memberKeyOf(memberId);
        final List<Long> deselectedSeatIds = seatSelectionStore.releaseAllByMember(performanceId, memberKey);
        logDeselectedSeats(performanceId, memberId, deselectedSeatIds);
        return DeselectedSeatIds.from(deselectedSeatIds);
    }

    public boolean deselectIfOwned(final Long performanceId, final Long seatId, final Long memberId) {
        return seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKeyOf(memberId));
    }

    public java.util.Set<Long> getSelectingSeatIds(final Long performanceId) {
        return seatSelectionStore.getSelectingSeatIds(performanceId);
    }

    private String memberKeyOf(final Long memberId) {
        return memberId.toString();
    }

    private void validateOwner(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final String memberKey,
            final String holder
    ) {
        if (memberKey.equals(holder)) {
            return;
        }
        logNotOwned(performanceId, seatId, memberId, holder);
        throw new CoreException(ErrorType.SEAT_NOT_OWNED);
    }

    private void releaseSeat(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final String memberKey
    ) {
        final boolean released = seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKey);
        if (released) {
            log.debug("좌석 선택 해제에 성공했습니다. performanceId={}, seatId={}, memberId={}", performanceId, seatId, memberId);
            return;
        }
        handleReleaseFailure(performanceId, seatId, memberId);
    }

    private void handleReleaseFailure(final Long performanceId, final Long seatId, final Long memberId) {
        final String currentHolder = seatSelectionStore.getHolder(performanceId, seatId);
        if (currentHolder == null) {
            log.debug("좌석 선택 해제 시점에 이미 만료되었거나 해제되었습니다. performanceId={}, seatId={}, memberId={}",
                    performanceId, seatId, memberId);
            return;
        }
        logNotOwned(performanceId, seatId, memberId, currentHolder);
        throw new CoreException(ErrorType.SEAT_NOT_OWNED);
    }

    private void logNotOwned(final Long performanceId, final Long seatId, final Long memberId, final String holder) {
        log.warn("좌석 선택 해제 권한이 없습니다. performanceId={}, seatId={}, requestMemberId={}, holderMemberId={}",
                performanceId, seatId, memberId, holder);
    }

    private void logDeselectedSeats(final Long performanceId, final Long memberId, final List<Long> seatIds) {
        for (final Long seatId : seatIds) {
            log.debug("좌석 일괄 선택 해제에 성공했습니다. performanceId={}, seatId={}, memberId={}", performanceId, seatId, memberId);
        }
    }
}
