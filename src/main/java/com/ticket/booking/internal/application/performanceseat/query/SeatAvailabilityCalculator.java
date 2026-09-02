package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.domain.performanceseat.model.PerformanceSeatState;
import com.ticket.booking.internal.application.performanceseat.query.model.AvailableSeatRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SeatAvailabilityCalculator {

    public List<GetSeatAvailabilityUseCase.GradeAvailability> calculate(
            final List<AvailableSeatRow> rows,
            final Set<Long> redisOccupiedSeatIds
    ) {
        if (rows.isEmpty()) {
            return List.of();
        }

        final Map<GradeKey, Long> availableSeatCounts = new LinkedHashMap<>();
        for (final AvailableSeatRow row : rows) {
            final GradeKey gradeKey = new GradeKey(row.gradeName(), row.sortOrder());
            availableSeatCounts.putIfAbsent(gradeKey, 0L);

            if (row.state() == PerformanceSeatState.AVAILABLE && !redisOccupiedSeatIds.contains(row.seatId())) {
                availableSeatCounts.computeIfPresent(gradeKey, (key, count) -> count + 1L);
            }
        }

        final List<GetSeatAvailabilityUseCase.GradeAvailability> grades = new ArrayList<>(availableSeatCounts.size());
        for (final Map.Entry<GradeKey, Long> entry : availableSeatCounts.entrySet()) {
            grades.add(new GetSeatAvailabilityUseCase.GradeAvailability(
                    entry.getKey().gradeName(),
                    entry.getKey().sortOrder(),
                    entry.getValue()
            ));
        }

        return grades;
    }

    private record GradeKey(String gradeName, int sortOrder) {
    }
}
