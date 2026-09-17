package com.ticket.booking.selection.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.concurrency.LockKey;
import com.ticket.booking.application.concurrency.LockManager;
import com.ticket.booking.application.concurrency.LockOptions;
import com.ticket.booking.application.port.SeatStatusEventPublisher;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.exception.PerformanceIsPastException;
import com.ticket.booking.exception.SeatAlreadyHeldException;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.selection.domain.SeatSelectionService;

import lombok.RequiredArgsConstructor;

/**
 * 좌석 한 자리의 선택 상태를 바꾸고, 그 결과를 좌석 상태 이벤트로 알린다.
 *
 * <p><b>상태 변경과 발행을 같은 좌석 락 안에서 한다.</b> 예전에는 락 안에서 상태만 바꾸고 발행은 락 밖(use case)에서 했다. 그러면 "A의 선택이 만료됨 →
 * B가 같은 좌석을 다시 선택 → A의 만료 처리가 뒤늦게 실행"에서 B의 SELECTED 뒤에 A의 DESELECTED가 나가, 이미 B가 잡은 좌석이 비어 보인다. 좌석
 * 단위 락이 select·deselect·만료·선점 해제를 모두 직렬화하므로, 발행까지 락 안으로 넣으면 서버가 내보내는 순서 자체가 상태 변화 순서와 같아진다.
 *
 * <p>서버가 보장하는 것은 여기까지다 — <b>클라이언트 수신 순서는 WebSocket 전송 계층의 문제이지 이 락의 범위가 아니다.</b> 그래서 만료·일괄 해제처럼 "이미
 * 지난 사실"을 알리는 경로는 발행 직전에 현재 상태를 락 안에서 다시 확인해, 남이 이미 차지한 좌석을 비었다고 알리지 않는다. 상태를 한 번 더 읽는 것만으로는 부족하고 그
 * 재확인이 락 안에 있어야 의미가 있다.
 */
@Component
@RequiredArgsConstructor
public class SeatSelectionCoordinator {
    /** 좌석 선택은 고빈도 경로라 경합 시 오래 기다리지 않고 빨리 실패한다. */
    private static final LockOptions SELECT_LOCK = LockOptions.waiting(Duration.ofMillis(500));

    /** 만료·일괄 해제 알림은 사용자 요청 경로가 아니라 조금 더 기다린다. */
    private static final LockOptions NOTIFY_LOCK = LockOptions.defaults();

    private final LockManager lockManager;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatStatusEventPublisher seatEventPublisher;
    private final Clock clock;

    public void select(
            final Long performanceId,
            final Long seatId,
            final Long memberId,
            final Long performanceSeatId,
            final LocalDateTime orderCloseTime) {
        lockManager.withLock(
                List.of(LockKey.seat(performanceId, seatId)),
                SELECT_LOCK,
                () -> {
                    if (LocalDateTime.now(clock).isAfter(orderCloseTime)) {
                        throw new PerformanceIsPastException(performanceId);
                    }
                    if (holdManager.isHeld(performanceId, seatId)) {
                        throw new SeatAlreadyHeldException(performanceId, seatId);
                    }
                    seatSelectionService.select(performanceId, seatId, memberId);
                    seatEventPublisher.publish(
                            performanceId, performanceSeatId, seatId, SeatStatusAction.SELECTED);
                });
    }

    /** 실제로 해제된 경우에만 알린다 — 이미 만료됐거나 없는 선택을 해제 요청했다고 해서 알림을 내보내지 않는다. */
    public void deselect(final Long performanceId, final Long seatId, final Long memberId) {
        final Long performanceSeatId = findPerformanceSeatId(performanceId, seatId);
        lockManager.withLock(
                List.of(LockKey.seat(performanceId, seatId)),
                SELECT_LOCK,
                () -> {
                    if (!seatSelectionService.deselect(performanceId, seatId, memberId)) {
                        return;
                    }
                    seatEventPublisher.publish(
                            performanceId, performanceSeatId, seatId, SeatStatusAction.DESELECTED);
                });
    }

    /**
     * 이미 끝난 선택(TTL 만료, 일괄 해제)을 알린다. 락 안에서 현재 상태를 다시 확인해, 그 사이 다른 사용자가 다시 선택했거나 선점으로 넘어간 좌석은 알리지
     * 않는다.
     */
    public void notifyReleasedIfFree(final Long performanceId, final Long seatId) {
        notifyReleasedIfFree(performanceId, seatId, findPerformanceSeatId(performanceId, seatId));
    }

    /**
     * performanceSeatId를 이미 알고 있을 때 쓴다. 여러 좌석을 한꺼번에 알릴 때 좌석마다 DB를 다시 읽지 않도록 호출자가 한 번에 조회한 값을 넘긴다.
     */
    public void notifyReleasedIfFree(
            final Long performanceId, final Long seatId, final @Nullable Long performanceSeatId) {
        lockManager.withLock(
                List.of(LockKey.seat(performanceId, seatId)),
                NOTIFY_LOCK,
                () -> {
                    if (seatSelectionService.isSelected(performanceId, seatId)) {
                        return;
                    }
                    if (holdManager.isHeld(performanceId, seatId)) {
                        return;
                    }
                    seatEventPublisher.publish(
                            performanceId, performanceSeatId, seatId, SeatStatusAction.DESELECTED);
                });
    }

    /** 락 밖에서 미리 읽는다 — DB 조회를 좌석 락 안에 넣으면 고빈도 경로의 락 보유 시간이 DB 지연을 그대로 따라간다. */
    private @Nullable Long findPerformanceSeatId(final Long performanceId, final Long seatId) {
        return performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(performanceId, List.of(seatId))
                .stream()
                .findFirst()
                .map(PerformanceSeat::getId)
                .orElse(null);
    }
}
