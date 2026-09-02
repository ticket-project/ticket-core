package com.ticket.core.app.performanceseat.query;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.hold.command.HoldManager;
import com.ticket.catalog.internal.domain.performance.Performance;
import com.ticket.catalog.internal.domain.performance.repository.PerformanceRepository;
import com.ticket.core.domain.performanceseat.command.SeatSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSeatAvailabilityUseCase {

    private final PerformanceRepository performanceRepository;
    private final SeatAvailabilityReadRepository seatAvailabilityReadRepository;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final SeatAvailabilityCalculator seatAvailabilityCalculator;

    public record Input(Long performanceId) {
        public Input {
            RequiredInput.positiveId(performanceId, "performanceId");
        }
    }

    public record Output(
            List<GradeAvailability> grades
    ) {}

    public record GradeAvailability(
            String gradeName,
            int sortOrder,
            long availableSeats
    ) {}

    public Output execute(Input input) {
        final Performance performance = performanceRepository.findWithQueuePolicyById(input.performanceId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA,
                        "공연을 찾을 수 없습니다. id=" + input.performanceId()));

        if (performance.getShow() == null) {
            throw new CoreException(ErrorType.NOT_FOUND_DATA,
                    "회차와 연결된 공연을 찾을 수 없습니다. id=" + input.performanceId());
        }

        return new Output(seatAvailabilityCalculator.calculate(
                seatAvailabilityReadRepository.findAvailableSeatRows(performance.getId(), performance.getShow().getId()),
                mergeRedisOccupiedIds(performance.getId())
        ));
    }

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> occupiedSeatIds = new HashSet<>(seatSelectionService.getSelectingSeatIds(performanceId));
        occupiedSeatIds.addAll(holdManager.getHoldingSeatIds(performanceId));
        return occupiedSeatIds;
    }
}
