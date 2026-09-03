package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.application.performanceseat.query.SeatAvailabilityReadRepository.PerformanceSeatStateRow;
import com.ticket.booking.internal.application.performanceseat.query.model.AvailableSeatRow;
import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.BookingPolicySnapshot;
import com.ticket.catalog.ShowLookup;
import com.ticket.catalog.ShowSeatMapEntry;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSeatAvailabilityUseCase {

    private final BookingPolicyLookup bookingPolicyLookup;
    private final ShowLookup showLookup;
    private final SeatAvailabilityReadRepository seatAvailabilityReadRepository;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;
    private final SeatAvailabilityCalculator seatAvailabilityCalculator;

    public record Input(Long performanceId) {
        public Input {
            if (performanceId == null) {
                throw new InvalidRequestException("performanceId는 필수입니다.");
            }
            if (performanceId <= 0) {
                throw new InvalidRequestException("performanceId는 양수여야 합니다.");
            }
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
        final BookingPolicySnapshot policy = bookingPolicyLookup.getBookingPolicy(input.performanceId(), List.of());

        final List<AvailableSeatRow> rows = toAvailableSeatRows(input.performanceId(), policy.showId());

        return new Output(seatAvailabilityCalculator.calculate(
                rows,
                mergeRedisOccupiedIds(input.performanceId())
        ));
    }

    private List<AvailableSeatRow> toAvailableSeatRows(final Long performanceId, final long showId) {
        final List<PerformanceSeatStateRow> stateRows = seatAvailabilityReadRepository.findSeatStates(performanceId);
        if (stateRows.isEmpty()) {
            return List.of();
        }

        final Map<Long, ShowSeatMapEntry> seatMapBySeatId = new HashMap<>();
        for (final ShowSeatMapEntry entry : showLookup.getSeatMap(showId)) {
            seatMapBySeatId.put(entry.seatId(), entry);
        }

        return stateRows.stream()
                .map(row -> toAvailableSeatRow(row, seatMapBySeatId.get(row.seatId())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private AvailableSeatRow toAvailableSeatRow(final PerformanceSeatStateRow row, final ShowSeatMapEntry entry) {
        if (entry == null) {
            return null;
        }
        return new AvailableSeatRow(row.seatId(), row.state(), entry.gradeName(), entry.gradeSortOrder());
    }

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> occupiedSeatIds = new HashSet<>(seatSelectionService.getSelectingSeatIds(performanceId));
        occupiedSeatIds.addAll(holdManager.getHoldingSeatIds(performanceId));
        return occupiedSeatIds;
    }
}
