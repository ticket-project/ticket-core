package com.ticket.booking.selection.domain;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.selection.domain.SeatSelectionStore.SelectResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionService {
    private static final Duration SELECT_TTL = Duration.ofMinutes(5);
    private final SeatSelectionStore seatSelectionStore;

    /** @param maxSeatCount 회차의 1인 선점 좌석 수 한도. 동시에 선택할 수 있는 좌석 수도 이 한도를 따른다. null이면 제한하지 않는다 */
    public void select(
            final Long performanceId, final Long seatId, final Long memberId, final @Nullable Integer maxSeatCount) {
        final String memberKey = memberKeyOf(memberId);
        final SelectResult result =
                seatSelectionStore.selectIfAbsent(performanceId, seatId, memberKey, SELECT_TTL, maxSeatCount);
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
     * @return 이 호출이 실제로 선택을 해제했으면 {@code true}. 이미 만료됐거나 선택 정보가 없으면 {@code false}다 — 호출자가 아무 일도 일어나지 않은 해제를 좌석 상태 알림으로
     *     내보내지 않게 하려고 결과를 돌려준다.
     */
    public boolean deselect(final Long performanceId, final Long seatId, final Long memberId) {
        final String memberKey = memberKeyOf(memberId);
        if (seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKey)) {
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

    /** 지금 이 좌석을 누군가 선택하고 있는지. 만료 알림 전에 락 안에서 다시 확인하는 용도다. */
    public boolean isSelected(final Long performanceId, final Long seatId) {
        return seatSelectionStore.getHolder(performanceId, seatId) != null;
    }

    /** @return 실제로 해제된 좌석 id. 호출자가 그 좌석만 골라 알림을 보낸다 */
    public List<Long> deselectAll(final Long performanceId, final Long memberId) {
        final List<Long> deselectedSeatIds =
                seatSelectionStore.releaseAllByMember(performanceId, memberKeyOf(memberId));
        log.debug(
                "좌석 일괄 선택 해제에 성공했습니다. performanceId={}, seatIds={}, memberId={}",
                performanceId,
                deselectedSeatIds,
                memberId);
        return deselectedSeatIds;
    }

    public boolean deselectIfOwned(final Long performanceId, final Long seatId, final Long memberId) {
        return seatSelectionStore.releaseIfOwned(performanceId, seatId, memberKeyOf(memberId));
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
