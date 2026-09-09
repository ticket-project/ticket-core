package com.ticket.booking.application;

import com.ticket.booking.application.port.SeatAvailabilityQueryPort;

import com.ticket.booking.application.port.SeatAvailabilityQueryPort.PerformanceSeatStateRow;
import com.ticket.booking.domain.PerformanceSeatState;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PerformanceGrade별 잔여석 수를 계산한다. 그룹 key는 변경 가능한 gradeName이 아니라
 * performanceGradeId다 — 표시 이름이 같아도 ID가 다른 grade를 합치지 않는다.
 */
@Component
public class SeatAvailabilityCalculator {

    public Map<Long, Long> calculate(
            final List<PerformanceSeatStateRow> rows,
            final Set<Long> redisOccupiedSeatIds
    ) {
        if (rows.isEmpty()) {
            return Map.of();
        }

        final Map<Long, Long> availableSeatCounts = new LinkedHashMap<>();
        for (final PerformanceSeatStateRow row : rows) {
            availableSeatCounts.putIfAbsent(row.performanceGradeId(), 0L);

            if (row.state() == PerformanceSeatState.AVAILABLE && !redisOccupiedSeatIds.contains(row.seatId())) {
                availableSeatCounts.computeIfPresent(row.performanceGradeId(), (key, count) -> count + 1L);
            }
        }

        return availableSeatCounts;
    }
}
