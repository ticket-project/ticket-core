package com.ticket.booking.event;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ticket.booking.hold.domain.HoldStore;
import com.ticket.booking.order.usecase.OrderHoldSnapshot;
import com.ticket.booking.seat.port.SeatStatusEvent;
import com.ticket.booking.seat.port.SeatStatusEvent.SeatStatusAction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문이 만들어진 뒤 그 좌석들에 남은 선택 상태를 정리하고, 좌석이 선점됐다는 사실을 좌석 상태 이벤트로 알린다.
 *
 * <p>선점 자체는 이 시점에 이미 Redis에 기록돼 있다. 여기서 하는 일은 선점자의 좌석 선택을 거두고 HELD를 발행하는 것이다. "아직 이 hold인가" 확인과 선택 정리·발행은 저장소 스크립트 하나가
 * 한다 — 좌석 락 없이도 이미 풀린 좌석에 HELD가 늦게 나가지 않는다.
 *
 * <p>DB 읽기는 listener의 snapshot reader에서 끝낸다. 이벤트가 재전달돼도 hold가 이미 끝났으면 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeldSeatPublisher {
    private final HoldStore holdStore;
    private final Clock clock;

    public void clearSelectionsAndPublishHeld(
            final String holdKey, final Long memberId, final OrderHoldSnapshot snapshot) {
        final LocalDateTime now = LocalDateTime.now(clock);
        final List<SeatStatusEvent> held = snapshot.seatIds().stream()
                .map(seatId -> new SeatStatusEvent(
                        snapshot.performanceId(),
                        snapshot.performanceSeatIdBySeatId().get(seatId),
                        seatId,
                        SeatStatusAction.HELD,
                        now))
                .toList();
        if (!holdStore.confirmHeld(snapshot.performanceId(), holdKey, memberId, held)) {
            log.debug("주문 생성 후처리를 건너뜁니다. hold가 이미 종료되었습니다. holdKey={}", holdKey);
        }
    }
}
