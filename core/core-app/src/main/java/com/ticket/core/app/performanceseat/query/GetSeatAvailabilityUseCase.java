package com.ticket.core.app.performanceseat.query;

import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.core.domain.performance.model.Performance;
import com.ticket.core.domain.performance.query.PerformanceFinder;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSeatAvailabilityUseCase {

    private final PerformanceFinder performanceFinder;
    private final SeatAvailabilityQueryRepository seatAvailabilityQueryRepository;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final SeatAvailabilityCalculator seatAvailabilityCalculator;

    public record Input(Long performanceId) {}

    public record Output(
            List<GradeAvailability> grades
    ) {}

    public record GradeAvailability(
            String gradeName,
            int sortOrder,
            long availableSeats
    ) {}

    public Output execute(Input input) {
        final Performance performance = performanceFinder.findById(input.performanceId());

        if (performance.getShow() == null) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA,
                    "회차와 연결된 공연을 찾을 수 없습니다. id=" + input.performanceId());
        }

        return new Output(seatAvailabilityCalculator.calculate(
                seatAvailabilityQueryRepository.findAvailableSeatRows(performance.getId(), performance.getShow().getId()),
                mergeRedisOccupiedIds(performance.getId())
        ));
    }

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> occupiedSeatIds = new HashSet<>(seatSelectionService.getSelectingSeatIds(performanceId));
        occupiedSeatIds.addAll(holdManager.getHoldingSeatIds(performanceId));
        return occupiedSeatIds;
    }
}
