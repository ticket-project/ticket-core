package com.ticket.booking.hold.domain;

import com.ticket.booking.support.domain.RequestedSeatIds;
import com.ticket.booking.hold.domain.Hold;
import com.ticket.booking.hold.domain.HoldManager;
import com.ticket.booking.domain.PerformanceSeat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 생성 절차 중 hold 배분·해제 단계다. {@code domain/hold}(hold aggregate 자체)가 아니라
 * {@code domain/order/command/create}에 있는 이유는 이 클래스가 hold 개념 자체가 아니라
 * "주문 생성이 hold를 어떻게 쓰는가"라는 order 생성 절차의 한 단계이기 때문이다 — hold의 저장·
 * 조회·해제 규칙 자체는 {@link HoldManager}(domain/hold)가 그대로 소유한다.
 */
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
