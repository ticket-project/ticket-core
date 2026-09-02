package com.ticket.booking.internal.domain.order.command.create;

import com.ticket.booking.internal.domain.hold.model.Hold;
import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HoldAllocator {

    private final HoldManager holdManager;

    /**
     * Redis hold만 담당한다. 좌석 가용성은 DB 검증 단계에서 이미 확인했으므로 그 결과를 받는다.
     * DB 조회와 Redis 호출을 한 메서드에 섞지 않는 것이 요점이다.
     */
    public HoldAllocation allocate(
            final Long memberId,
            final Long performanceId,
            final RequestedSeatIds requestedSeatIds,
            final List<PerformanceSeat> performanceSeats,
            final Duration holdDuration,
            final LocalDateTime now
    ) {
        final Hold hold = holdManager.createHold(memberId, performanceId, requestedSeatIds, holdDuration, now);
        return new HoldAllocation(hold, performanceSeats);
    }

    public void release(final HoldAllocation allocation) {
        holdManager.release(
                allocation.hold().performanceId(),
                allocation.hold().holdKey(),
                allocation.hold().seatIds()
        );
    }
}
