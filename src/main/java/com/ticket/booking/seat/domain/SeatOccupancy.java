package com.ticket.booking.seat.domain;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.selection.domain.SeatSelectionService;

import lombok.RequiredArgsConstructor;

/**
 * Redis가 점유로 보는 좌석을 판정한다 — 누군가 고르는 중(selection)이거나 이미 선점(hold)한 좌석이다.
 *
 * <p>"무엇을 점유로 보는가"는 좌석 상태 조회, 잔여석 조회, 선택 만료 알림, 선점 해제 알림이 모두 같아야 한다. 조건이 바뀌면 여기 한 곳만 고친다. DB의 좌석 판매 상태는 보지 않는다 — 그것은
 * 호출자가 {@link PerformanceSeat}로 따로 본다.
 */
@Component
@RequiredArgsConstructor
public class SeatOccupancy {
    private final SeatSelectionService seatSelectionService;
    private final HoldRegistry holdRegistry;

    /** 회차 전체의 점유 좌석이다. 좌석마다 묻지 않고 선택·선점 index를 한 번씩 읽는다. */
    public Set<Long> occupiedSeatIds(final Long performanceId) {
        final Set<Long> selectingSeatIds = seatSelectionService.getSelectingSeatIds(performanceId);
        final Set<Long> holdingSeatIds = holdRegistry.getHoldingSeatIds(performanceId);

        final Set<Long> occupiedSeatIds = HashSet.newHashSet(selectingSeatIds.size() + holdingSeatIds.size());
        occupiedSeatIds.addAll(selectingSeatIds);
        occupiedSeatIds.addAll(holdingSeatIds);
        return occupiedSeatIds;
    }

    /** 좌석 한 자리가 지금 점유돼 있는지. 좌석 key를 직접 읽으므로 좌석 락 안의 재확인에 쓴다. */
    public boolean isOccupied(final Long performanceId, final Long seatId) {
        return seatSelectionService.isSelected(performanceId, seatId) || holdRegistry.isHeld(performanceId, seatId);
    }
}
