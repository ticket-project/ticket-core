package com.ticket.booking.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ticket.booking.application.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.concurrency.LockKey;
import com.ticket.booking.application.concurrency.LockManager;
import com.ticket.booking.application.concurrency.LockOptions;
import com.ticket.booking.application.port.HoldReleaseProgressRecorder;
import com.ticket.booking.application.port.SeatStatusEventPublisher;
import com.ticket.booking.domain.seat.PerformanceSeat;
import com.ticket.booking.domain.seat.PerformanceSeatRepository;
import com.ticket.booking.domain.selection.SeatSelectionService;
import com.ticket.booking.hold.domain.HoldManager;

import lombok.RequiredArgsConstructor;

/**
 * 주문이 끝난 뒤 좌석 선점을 풀고, 그 결과를 좌석 상태 이벤트로 알린다.
 *
 * <p><b>좌석 락 안에서 해제와 발행을 함께 한다</b> — {@link SeatSelectionCoordinator}와 같은 이유다. 락 밖에서 발행하면 뒤늦은 해제
 * 알림이 다른 사용자의 선택 뒤에 끼어들어, 이미 잡힌 좌석이 비어 보인다.
 *
 * <p>해제는 {@link HoldReleaseProgressRecorder}로 한 번만 수행한다 — 이 후속 처리는 트랜잭션 없이 실행되는 listener에서 불리고 이벤트가
 * 재전달될 수 있다. 발행 대상도 락 안에서 현재 상태를 다시 확인해 고른다.
 */
@Component
@RequiredArgsConstructor
public class HoldReleaseCoordinator {
    private final LockManager lockManager;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatStatusEventPublisher seatStatusEventPublisher;
    private final HoldReleaseProgressRecorder progressRecorder;

    public void releaseAndPublish(
            final UUID eventId, final HoldReleaseTask task, final LocalDateTime now) {
        lockManager.withLock(
                LockKey.seats(task.performanceId(), task.seatIds()),
                LockOptions.defaults(),
                () -> releaseAndPublishLocked(eventId, task, now));
    }

    private void releaseAndPublishLocked(
            final UUID eventId, final HoldReleaseTask task, final LocalDateTime now) {
        releaseHoldOnce(eventId, task, now);
        final List<Long> publishableSeatIds = findCurrentlyAvailableSeats(task);
        if (publishableSeatIds.isEmpty()) {
            return;
        }
        final Map<Long, Long> performanceSeatIdBySeatId =
                resolvePerformanceSeatIds(task, publishableSeatIds);
        for (final Long seatId : publishableSeatIds) {
            seatStatusEventPublisher.publish(
                    task.performanceId(),
                    performanceSeatIdBySeatId.get(seatId),
                    seatId,
                    SeatStatusAction.RELEASED);
        }
    }

    private Map<Long, Long> resolvePerformanceSeatIds(
            final HoldReleaseTask task, final List<Long> seatIds) {
        return performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(task.performanceId(), seatIds)
                .stream()
                .collect(Collectors.toMap(PerformanceSeat::getSeatId, PerformanceSeat::getId));
    }

    private void releaseHoldOnce(
            final UUID eventId, final HoldReleaseTask task, final LocalDateTime now) {
        if (task.holdReleased()) {
            return;
        }
        holdManager.release(task.performanceId(), task.holdKey(), task.seatIds());
        progressRecorder.recordHoldReleased(eventId, now);
    }

    private List<Long> findCurrentlyAvailableSeats(final HoldReleaseTask task) {
        final Set<Long> selectedSeatIds =
                seatSelectionService.getSelectingSeatIds(task.performanceId());
        return task.seatIds().stream()
                .filter(seatId -> !holdManager.isHeld(task.performanceId(), seatId))
                .filter(seatId -> !selectedSeatIds.contains(seatId))
                .toList();
    }
}
