package com.ticket.booking.internal.infrastructure.redis;

import com.ticket.booking.internal.application.performanceseat.event.SeatStatusEventPublisher;
import com.ticket.booking.internal.infrastructure.performanceseat.store.SeatSelectionRedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.ticket.booking.internal.application.performanceseat.event.SeatStatusEvent.SeatStatusAction.DESELECTED;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionExpirationHandler implements RedisKeyExpirationHandler {

    private final SeatStatusEventPublisher seatEventPublisher;

    @Override
    public boolean supports(final String expiredKey) {
        return SeatSelectionRedisKey.tryParseSelectKey(expiredKey).isPresent();
    }

    @Override
    public void handle(final String expiredKey) {
        final SeatSelectionRedisKey.SelectKey selectKey = SeatSelectionRedisKey.tryParseSelectKey(expiredKey)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 좌석 선택 만료 키입니다: " + expiredKey));

        seatEventPublisher.publish(selectKey.performanceId(), selectKey.seatId(), DESELECTED);
        log.info("좌석 선택 만료 이벤트 처리: performanceId={}, seatId={}",
                selectKey.performanceId(), selectKey.seatId());
    }
}
