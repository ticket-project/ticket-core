package com.ticket.booking.selection.persistence;

import org.springframework.stereotype.Component;

import com.ticket.booking.redis.RedisKeyExpirationHandler;
import com.ticket.booking.selection.usecase.SeatSelectionCoordinator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 좌석 선택 TTL 만료 키를 해석해 application에 넘긴다. <b>Redis key 해석과 호출만 한다</b> — 대상 좌석 확인, 현재 상태 확인, 알림 필요 여부 판단은
 * {@link SeatSelectionCoordinator}가 좌석 락 안에서 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionExpirationHandler implements RedisKeyExpirationHandler {
    private final SeatSelectionCoordinator seatSelectionCoordinator;

    @Override
    public boolean supports(final String expiredKey) {
        return SeatSelectionRedisKey.tryParseSelectKey(expiredKey).isPresent();
    }

    @Override
    public void handle(final String expiredKey) {
        final SeatSelectionRedisKey.SelectKey selectKey = SeatSelectionRedisKey.tryParseSelectKey(expiredKey)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 좌석 선택 만료 키입니다: " + expiredKey));

        seatSelectionCoordinator.notifyReleasedIfFree(selectKey.performanceId(), selectKey.seatId());
        log.info("좌석 선택 만료 이벤트 처리: performanceId={}, seatId={}", selectKey.performanceId(), selectKey.seatId());
    }
}
