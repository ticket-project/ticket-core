package com.ticket.booking.selection.usecase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.ticket.booking.exception.BookingErrorCode;
import com.ticket.booking.exception.BookingException;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.selection.domain.SeatSelectionService;

import lombok.RequiredArgsConstructor;

/**
 * 좌석 한 자리의 선택 상태를 바꾸고, 그 결과를 좌석 상태 이벤트로 알린다.
 *
 * <p><b>상태 확인·변경·발행은 저장소의 Lua 스크립트 하나가 한다</b>(좌석 락 재설계 C안). Redis가 스크립트를 하나씩 실행하므로 좌석 락 없이도 "A의 선택이 만료됨 → B가 같은 좌석을 다시
 * 선택 → A의 만료 알림이 뒤늦게 실행"에서 B의 SELECTED 뒤에 A의 DESELECTED가 나가지 않는다 — 만료 알림 스크립트가 발행 직전에 선택·선점 키를 같은 순간에 본다. 알림은 relay 채널
 * 하나로 나가 서버 대수와 무관하게 그 순서대로 각 인스턴스의 사용자에게 간다.
 *
 * <p>서버가 보장하는 것은 여기까지다 — <b>클라이언트 수신 순서는 WebSocket 전송 계층의 문제다.</b>
 */
@Component
@RequiredArgsConstructor
public class SeatSelectionWriter {
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final Clock clock;

    public void select(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final Long performanceSeatId,
            final LocalDateTime orderCloseTime,
            final @Nullable Integer maxSeatCount) {
        final LocalDateTime now = LocalDateTime.now(clock);
        if (now.isAfter(orderCloseTime)) {
            throw new BookingException(BookingErrorCode.E3001);
        }
        seatSelectionService.select(
                performanceId,
                seatId,
                memberId,
                maxSeatCount,
                new SeatStatusEvent(performanceId, performanceSeatId, seatId, SeatStatusAction.SELECTED, now));
    }

    /** 실제로 해제됐고 hold가 없는 경우에만 알린다. 주문 생성 후 남아 있던 선택을 해제해도 HELD 상태를 덮어쓰지 않는다. */
    public void deselect(final Long performanceId, final Long seatId, final Long memberId) {
        seatSelectionService.deselect(
                performanceId,
                seatId,
                memberId,
                deselected(performanceId, seatId, findPerformanceSeatId(performanceId, seatId)));
    }

    /** 본인 선택이면 해제하고 알린다. 남의 선택이면 조용히 넘어간다(일괄 해제). */
    public void deselectIfOwned(
            final Long performanceId, final Long seatId, final Long memberId, final @Nullable Long performanceSeatId) {
        seatSelectionService.deselectIfOwned(
                performanceId, seatId, memberId, deselected(performanceId, seatId, performanceSeatId));
    }

    /** 이미 끝난 선택(TTL 만료)을 알린다. 그 사이 다른 사용자가 다시 선택했거나 선점으로 넘어간 좌석은 알리지 않는다. */
    public void notifyReleasedIfFree(
            final Long performanceId, final Long seatId, final @Nullable Long performanceSeatId) {
        seatSelectionService.notifyIfFree(performanceId, seatId, deselected(performanceId, seatId, performanceSeatId));
    }

    private SeatStatusEvent deselected(
            final Long performanceId, final Long seatId, final @Nullable Long performanceSeatId) {
        return new SeatStatusEvent(
                performanceId, performanceSeatId, seatId, SeatStatusAction.DESELECTED, LocalDateTime.now(clock));
    }

    /** 알림 표시값이다. 회차 판매 좌석을 찾지 못하면 null이다 — 없어도 발행을 막지 않는다. */
    private @Nullable Long findPerformanceSeatId(final Long performanceId, final Long seatId) {
        return performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(performanceId, List.of(seatId)).stream()
                .findFirst()
                .map(PerformanceSeat::getId)
                .orElse(null);
    }
}
