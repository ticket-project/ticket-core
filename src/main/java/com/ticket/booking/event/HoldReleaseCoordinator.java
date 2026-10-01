package com.ticket.booking.event;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ticket.booking.concurrency.DistributedLock;
import com.ticket.booking.concurrency.LockKey;
import com.ticket.booking.concurrency.LockOptions;
import com.ticket.booking.hold.domain.HoldRegistry;
import com.ticket.booking.seat.domain.SeatOccupancy;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;
import com.ticket.booking.seat.port.SeatStatusEventPublisher;
import com.ticket.booking.selection.usecase.SeatSelectionCoordinator;

import lombok.RequiredArgsConstructor;

/**
 * 주문이 끝난 뒤 좌석 선점을 풀고, 그 결과를 좌석 상태 이벤트로 알린다.
 *
 * <p><b>좌석 락 안에서 해제와 발행을 함께 한다</b> — {@link SeatSelectionCoordinator}와 같은 이유다. 락 밖에서 발행하면 뒤늦은 해제 알림이 다른 사용자의 선택 뒤에
 * 끼어들어, 이미 잡힌 좌석이 비어 보인다.
 *
 * <p>이벤트가 재전달되면 해제도 다시 수행한다 — {@link HoldRegistry#release}는 좌석에 아직 이 holdKey가 남아 있을 때만 지우므로, 반복해도 그사이 다른 사용자가 잡은 선점을
 * 건드리지 않는다. 발행 대상도 락 안에서 현재 상태를 다시 확인해 고른다.
 */
@Component
@RequiredArgsConstructor
public class HoldReleaseCoordinator {
    private final DistributedLock distributedLock;
    private final HoldRegistry holdRegistry;
    private final SeatOccupancy seatOccupancy;
    private final SeatStatusEventPublisher seatStatusEventPublisher;

    public void releaseAndPublish(final HoldReleaseTask task) {
        distributedLock.withLock(
                LockKey.seats(task.performanceId(), task.seatIds()),
                LockOptions.defaults(),
                () -> releaseAndPublishLocked(task));
    }

    private void releaseAndPublishLocked(final HoldReleaseTask task) {
        holdRegistry.release(task.performanceId(), task.holdKey(), task.seatIds());
        final List<Long> publishableSeatIds = findCurrentlyAvailableSeats(task);
        if (publishableSeatIds.isEmpty()) {
            return;
        }
        for (final Long seatId : publishableSeatIds) {
            seatStatusEventPublisher.publish(
                    task.performanceId(),
                    task.performanceSeatIdBySeatId().get(seatId),
                    seatId,
                    SeatStatusAction.RELEASED);
        }
    }

    private List<Long> findCurrentlyAvailableSeats(final HoldReleaseTask task) {
        return task.seatIds().stream()
                .filter(seatId -> !seatOccupancy.isOccupied(task.performanceId(), seatId))
                .toList();
    }
}
