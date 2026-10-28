package com.ticket.booking.event;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.order.usecase.OrderHoldSnapshot;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;

import lombok.RequiredArgsConstructor;

/**
 * 주문이 끝난 뒤 좌석 선점을 풀고, 그 결과를 좌석 상태 이벤트로 알린다.
 *
 * <p>해제와 "비었는가" 확인·발행은 저장소 스크립트 하나가 한다 — 좌석 락 없이도 뒤늦은 해제 알림이 다른 사용자의 선택 뒤에 끼어들지 않는다.
 *
 * <p>이벤트가 재전달되면 해제도 다시 수행한다 — {@link HoldStore#release}는 좌석에 아직 이 holdKey가 남아 있을 때만 지우므로, 반복해도 그사이 다른 사용자가 잡은 선점을 건드리지
 * 않는다.
 */
@Component
@RequiredArgsConstructor
public class HoldReleaser {
    private final HoldStore holdStore;
    private final Clock clock;

    public void releaseAndPublish(final String holdKey, final OrderHoldSnapshot snapshot) {
        final LocalDateTime now = LocalDateTime.now(clock);
        holdStore.release(
                snapshot.performanceId(),
                holdKey,
                snapshot.seatIds().stream()
                        .map(seatId -> new SeatStatusEvent(
                                snapshot.performanceId(),
                                snapshot.performanceSeatIdBySeatId().get(seatId),
                                seatId,
                                SeatStatusAction.RELEASED,
                                now))
                        .toList());
    }
}
