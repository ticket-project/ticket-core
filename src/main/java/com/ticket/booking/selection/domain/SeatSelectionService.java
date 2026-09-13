package com.ticket.booking.selection.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ticket.booking.exception.SeatAlreadySelectedException;
import com.ticket.booking.exception.SeatNotOwnedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionService {
    private static final Duration SELECT_TTL = Duration.ofMinutes(5);
    private final SeatSelectionStore seatSelectionStore;

    public void select(final Long performanceId, final Long seatId, final Long memberId) {
        final String memberKey = memberKeyOf(memberId);
        final boolean locked =
                seatSelectionStore.selectIfAbsent(performanceId, seatId, memberKey, SELECT_TTL);
        if (!locked) {
            log.warn(
                    "좌석 선택에 실패했습니다. performanceId={}, seatId={}, memberId={}",
                    performanceId,
                    seatId,
                    memberId);
            throw new SeatAlreadySelectedException(performanceId, seatId);
        }
        log.debug(
                "좌석 선택에 성공했습니다. performanceId={}, seatId={}, memberId={}",
                performanceId,
                seatId,
                memberId);
    }

    /**
     * @return 이 호출이 실제로 선택을 해제했으면 {@code true}. 이미 만료됐거나 선택 정보가 없으면 {@code false}다 — 호출자가 아무 일도
     *     일어나지 않은 해제를 좌석 상태 알림으로 내보내지 않게 하려고 결과를 돌려준다.
     */
    public boolean deselect(final Long performanceId, final Long seatId, final Long memberId) {
        final String memberKey = memberKeyOf(memberId);
        final String holder = seatSelectionStore.getHolder(performanceId, seatId);
        if (holder == null) {
            log.debug(
                    "좌석 선택 해제를 건너뜁니다. 이미 선택 정보가 없습니다. performanceId={}, seatId={}",
                    performanceId,
                    seatId);
            return false;
        }
        validateOwner(performanceId, seatId, memberId, memberKey, holder);
        return releaseSeat(performanceId, seatId, memberId, memberKey);
    }

    /** 지금 이 좌석을 누군가 선택하고 있는지. 만료 알림 전에 락 안에서 다시 확인하는 용도다. */
    public boolean isSelected(final Long performanceId, final Long seatId) {
        return seatSelectionStore.getHolder(performanceId, seatId) != null;
    }

    public DeselectedSeatIds deselectAll(final Long performanceId, final Long memberId) {
        final String memberKey = memberKeyOf(memberId);
        final List<Long> deselectedSeatIds =
                seatSelectionStore.releaseAllByMember(performanceId, memberKey);
        logDeselectedSeats(performanceId, memberId, deselectedSeatIds);
        return DeselectedSeatIds.from(deselectedSeatIds);
    }

    public boolean deselectIfOwned(
            final Long performanceId, final Long seatId, final Long memberId) {
        return seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKeyOf(memberId));
    }

    public Set<Long> getSelectingSeatIds(final Long performanceId) {
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
            final String holder) {
        if (memberKey.equals(holder)) {
            return;
        }
        logNotOwned(performanceId, seatId, memberId, holder);
        throw new SeatNotOwnedException(performanceId, seatId, memberId);
    }

    private boolean releaseSeat(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final String memberKey) {
        final boolean released =
                seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKey);
        if (released) {
            log.debug(
                    "좌석 선택 해제에 성공했습니다. performanceId={}, seatId={}, memberId={}",
                    performanceId,
                    seatId,
                    memberId);
            return true;
        }
        handleReleaseFailure(performanceId, seatId, memberId);
        return false;
    }

    private void handleReleaseFailure(
            final Long performanceId, final Long seatId, final Long memberId) {
        final String currentHolder = seatSelectionStore.getHolder(performanceId, seatId);
        if (currentHolder == null) {
            log.debug(
                    "좌석 선택 해제 시점에 이미 만료되었거나 해제되었습니다. performanceId={}, seatId={}, memberId={}",
                    performanceId,
                    seatId,
                    memberId);
            return;
        }
        logNotOwned(performanceId, seatId, memberId, currentHolder);
        throw new SeatNotOwnedException(performanceId, seatId, memberId);
    }

    private void logNotOwned(
            final Long performanceId, final Long seatId, final Long memberId, final String holder) {
        log.warn(
                "좌석 선택 해제 권한이 없습니다. performanceId={}, seatId={}, requestMemberId={}, holderMemberId={}",
                performanceId,
                seatId,
                memberId,
                holder);
    }

    private void logDeselectedSeats(
            final Long performanceId, final Long memberId, final List<Long> seatIds) {
        for (final Long seatId : seatIds) {
            log.debug(
                    "좌석 일괄 선택 해제에 성공했습니다. performanceId={}, seatId={}, memberId={}",
                    performanceId,
                    seatId,
                    memberId);
        }
    }
}
