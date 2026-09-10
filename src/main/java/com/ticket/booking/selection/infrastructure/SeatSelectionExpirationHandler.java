package com.ticket.booking.selection.infrastructure;

import com.ticket.booking.infrastructure.RedisKeyExpirationHandler;
import com.ticket.booking.seat.application.SeatStatusEventPublisher;
import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.booking.selection.infrastructure.SeatSelectionRedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ticket.booking.seat.application.SeatStatusEvent.SeatStatusAction.DESELECTED;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatSelectionExpirationHandler implements RedisKeyExpirationHandler {

    private final SeatStatusEventPublisher seatEventPublisher;
    private final PerformanceSeatRepository performanceSeatRepository;

    @Override
    public boolean supports(final String expiredKey) {
        return SeatSelectionRedisKey.tryParseSelectKey(expiredKey).isPresent();
    }

    @Override
    public void handle(final String expiredKey) {
        final SeatSelectionRedisKey.SelectKey selectKey = SeatSelectionRedisKey.tryParseSelectKey(expiredKey)
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 좌석 선택 만료 키입니다: " + expiredKey));

        final Long performanceSeatId = resolvePerformanceSeatId(selectKey);
        seatEventPublisher.publish(selectKey.performanceId(), performanceSeatId, DESELECTED);
        log.info("좌석 선택 만료 이벤트 처리: performanceId={}, seatId={}, performanceSeatId={}",
                selectKey.performanceId(), selectKey.seatId(), performanceSeatId);
    }

    private Long resolvePerformanceSeatId(final SeatSelectionRedisKey.SelectKey selectKey) {
        return performanceSeatRepository
                .findAllByPerformanceIdAndSeatIdIn(selectKey.performanceId(), List.of(selectKey.seatId()))
                .stream()
                .findFirst()
                .map(PerformanceSeat::getId)
                .orElse(null);
    }
}
