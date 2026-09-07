package com.ticket.booking.application.hold.command;

import com.ticket.booking.domain.hold.command.HoldManager;
import com.ticket.booking.domain.performanceseat.command.SeatSelectionService;
import com.ticket.booking.domain.performanceseat.model.PerformanceSeat;
import com.ticket.booking.domain.performanceseat.repository.PerformanceSeatRepository;
import com.ticket.booking.application.performanceseat.event.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.application.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.booking.application.hold.event.HoldReleaseProgressRecorder;
import com.ticket.booking.application.lock.LockKey;
import com.ticket.booking.application.lock.LockManager;
import com.ticket.booking.application.lock.LockOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class HoldReleaseTaskProcessor {

    private final LockManager lockManager;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final PerformanceSeatRepository performanceSeatRepository;
    private final SeatStatusEventPublisher seatStatusEventPublisher;
    private final HoldReleaseProgressRecorder progressRecorder;

    public void process(final UUID eventId, final HoldReleaseTask task, final LocalDateTime now) {
        lockManager.withLock(
                LockKey.seats(task.performanceId(), task.seatIds()),
                LockOptions.defaults(),
                () -> releaseAndPublish(eventId, task, now)
        );
    }

    private void releaseAndPublish(final UUID eventId, final HoldReleaseTask task, final LocalDateTime now) {
        releaseHoldOnce(eventId, task, now);
        final List<Long> publishableSeatIds = findCurrentlyAvailableSeats(task);
        if (publishableSeatIds.isEmpty()) {
            return;
        }
        final Map<Long, Long> performanceSeatIdBySeatId = resolvePerformanceSeatIds(task, publishableSeatIds);
        for (final Long seatId : publishableSeatIds) {
            seatStatusEventPublisher.publish(
                    task.performanceId(), performanceSeatIdBySeatId.get(seatId), SeatStatusAction.RELEASED);
        }
    }

    private Map<Long, Long> resolvePerformanceSeatIds(final HoldReleaseTask task, final List<Long> seatIds) {
        return performanceSeatRepository.findAllByPerformanceIdAndSeatIdIn(task.performanceId(), seatIds).stream()
                .collect(Collectors.toMap(PerformanceSeat::getSeatId, PerformanceSeat::getId));
    }

    private void releaseHoldOnce(
            final UUID eventId,
            final HoldReleaseTask task,
            final LocalDateTime now
    ) {
        if (task.holdReleased()) {
            return;
        }
        holdManager.release(task.performanceId(), task.holdKey(), task.seatIds());
        progressRecorder.recordHoldReleased(eventId, now);
    }

    private List<Long> findCurrentlyAvailableSeats(final HoldReleaseTask task) {
        final Set<Long> selectedSeatIds = seatSelectionService.getSelectingSeatIds(task.performanceId());
        return task.seatIds().stream()
                .filter(seatId -> !holdManager.isHeld(task.performanceId(), seatId))
                .filter(seatId -> !selectedSeatIds.contains(seatId))
                .toList();
    }
}
