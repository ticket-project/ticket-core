package com.ticket.booking.selection.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ticket.booking.redis.RedisKeyExpirationHandler;
import com.ticket.booking.selection.usecase.SeatSelectionWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 좌석 선택 TTL 만료 키를 해석해 application에 넘긴다. <b>Redis key 해석과 호출만 한다</b> — 대상 좌석 확인, 현재 상태 확인, 알림 필요 여부 판단은
 * {@link SeatSelectionWriter}가 좌석 락 안에서 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionExpirationHandler implements RedisKeyExpirationHandler {
    private final SeatSelectionWriter seatSelectionWriter;

    @Override
    public boolean handle(final String expiredKey) {
        final Optional<SeatSelectionRedisKey.SelectKey> parsed = SeatSelectionRedisKey.tryParseSelectKey(expiredKey);
        if (parsed.isEmpty()) {
            return false;
        }
        final SeatSelectionRedisKey.SelectKey selectKey = parsed.get();
        seatSelectionWriter.notifyReleasedIfFree(selectKey.performanceId(), selectKey.seatId());
        log.info("좌석 선택 만료 이벤트 처리: performanceId={}, seatId={}", selectKey.performanceId(), selectKey.seatId());
        return true;
    }
}
