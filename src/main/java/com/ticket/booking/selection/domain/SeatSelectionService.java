package com.ticket.booking.selection.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.selection.domain.SeatSelectionStore.SelectResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionService {
    private static final Duration SELECT_TTL = Duration.ofMinutes(5);
    private final SeatSelectionStore seatSelectionStore;

    /**
     * 선택하고 {@code selected}를 발행한다. 선점된 좌석이면 E6000이다 — 저장소 스크립트가 선점 확인·선택·발행을 한 번에 하므로 좌석 락이 필요 없다.
     *
     * @param maxSeatCount 회차의 1인 선점 좌석 수 한도. 동시에 선택할 수 있는 좌석 수도 이 한도를 따른다. null이면 제한하지 않는다
     */
    public void select(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final @Nullable Integer maxSeatCount,
            final SeatStatusEvent selected) {
        final String memberKey = memberKeyOf(memberId);
        final SelectResult result =
                seatSelectionStore.selectIfAbsent(performanceId, seatId, memberKey, SELECT_TTL, maxSeatCount, selected);
        if (result == SelectResult.HELD) {
            throw new BookingException(BookingErrorCode.E6000);
        }
        if (result == SelectResult.ALREADY_SELECTED) {
            log.debug("좌석 선택에 실패했습니다. performanceId={}, seatId={}, memberId={}", performanceId, seatId, memberId);
            throw new BookingException(BookingErrorCode.E4001);
        }
        if (result == SelectResult.LIMIT_EXCEEDED) {
            throw new BookingException(BookingErrorCode.E6001);
        }
        log.debug("좌석 선택에 성공했습니다. performanceId={}, seatId={}, memberId={}", performanceId, seatId, memberId);
    }

    /**
     * 본인 선택을 해제하고, 실제로 지웠고 선점되지 않은 좌석이면 {@code deselected}를 발행한다. 남의 선택이면 E4002다.
     *
     * @return 이 호출이 실제로 선택을 해제했으면 {@code true}. 이미 만료됐거나 선택 정보가 없으면 {@code false}다
     */
    public boolean deselect(
            final Long performanceId, final Long seatId, final Long memberId, final SeatStatusEvent deselected) {
        final String memberKey = memberKeyOf(memberId);
        if (seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKey, deselected)) {
            log.debug("좌석 선택 해제에 성공했습니다. performanceId={}, seatId={}, memberId={}", performanceId, seatId, memberId);
            return true;
        }
        final String holder = seatSelectionStore.getHolder(performanceId, seatId);
        if (holder == null) {
            log.debug("좌석 선택 해제를 건너뜁니다. 이미 선택 정보가 없습니다. performanceId={}, seatId={}", performanceId, seatId);
            return false;
        }
        logNotOwned(performanceId, seatId, memberId, holder);
        throw new BookingException(BookingErrorCode.E4002);
    }

    /** 지금 이 좌석을 누군가 선택하고 있는지. */
    public boolean isSelected(final Long performanceId, final Long seatId) {
        return seatSelectionStore.getHolder(performanceId, seatId) != null;
    }

    /** 이 회원이 지금 선택 중인 좌석. 일괄 해제가 좌석마다 알림 표시값을 미리 읽으려고 쓴다. */
    public Set<Long> getSelectedSeatIds(final Long performanceId, final Long memberId) {
        return seatSelectionStore.getSelectedSeatIdsByMember(performanceId, memberKeyOf(memberId));
    }

    /**
     * 본인 선택이면 해제한다. 남의 선택이어도 오류로 보지 않는다. {@code deselected}가 있으면 지웠고 선점되지 않은 좌석일 때 발행한다.
     *
     * @return 이 호출이 지웠으면 true
     */
    public boolean deselectIfOwned(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final @Nullable SeatStatusEvent deselected) {
        return seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKeyOf(memberId), deselected);
    }

    /** 이미 끝난 선택(TTL 만료)을 알린다. 선택도 선점도 없는 좌석일 때만 발행한다 — 그 사이 다시 선택됐거나 선점으로 넘어간 좌석은 알리지 않는다. */
    public void notifyIfFree(final Long performanceId, final Long seatId, final SeatStatusEvent deselected) {
        seatSelectionStore.publishIfFree(performanceId, seatId, deselected);
    }

    public Set<Long> getSelectingSeatIds(final Long performanceId) {
        return seatSelectionStore.getSelectingSeatIds(performanceId);
    }

    /**
     * 주문하려는 좌석이 모두 이 회원이 지금 선택 중인 좌석인지 확인한다(ADR 0021).
     *
     * <p>빠진 좌석이 모두 최근에 선택 시간이 지나 풀린 것이면 E4007, 하나라도 선택한 적 없거나 남이 선택한 좌석이면 E4006이다. 만료 기록은 실패한 경우에만 읽는다 — 정상 주문은 Redis를
     * 한 번만 부른다.
     */
    public void requireSelectedBy(final Long performanceId, final Long memberId, final List<Long> seatIds) {
        final String memberKey = memberKeyOf(memberId);
        final Set<Long> selected = seatSelectionStore.getSelectedSeatIdsByMember(performanceId, memberKey);
        final List<Long> missing =
                seatIds.stream().filter(seatId -> !selected.contains(seatId)).toList();
        if (missing.isEmpty()) {
            return;
        }
        if (seatSelectionStore
                .getRecentlyExpiredSeatIdsByMember(performanceId, memberKey)
                .containsAll(missing)) {
            throw new BookingException(BookingErrorCode.E4007);
        }
        throw new BookingException(BookingErrorCode.E4006);
    }

    private String memberKeyOf(final Long memberId) {
        return memberId.toString();
    }

    private void logNotOwned(final Long performanceId, final Long seatId, final Long memberId, final String holder) {
        log.warn(
                "좌석 선택 해제 권한이 없습니다. performanceId={}, seatId={}, requestMemberId={}, holderMemberId={}",
                performanceId,
                seatId,
                memberId,
                holder);
    }
}
