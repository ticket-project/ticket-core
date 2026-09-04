package com.ticket.booking.internal.application.performanceseat.query;

import com.ticket.booking.internal.domain.hold.command.HoldManager;
import com.ticket.booking.internal.application.performanceseat.query.SeatAvailabilityReadRepository.PerformanceSeatStateRow;
import com.ticket.booking.internal.application.performanceseat.query.model.AvailableSeatRow;
import com.ticket.booking.internal.domain.performanceseat.command.SeatSelectionService;
import com.ticket.catalog.BookingPolicyLookup;
import com.ticket.catalog.PerformanceSaleCatalog;
import com.ticket.catalog.PerformanceSaleSnapshot;
import com.ticket.error.InvalidRequestException;
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

    private final BookingPolicyLookup bookingPolicyLookup;
    private final PerformanceSaleCatalog performanceSaleCatalog;
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
        // 회차 예매 정책 조회는 회차 존재 확인을 겸한다.
        bookingPolicyLookup.getBookingPolicy(input.performanceId());

        final List<AvailableSeatRow> rows = toAvailableSeatRows(input.performanceId());

        return new Output(seatAvailabilityCalculator.calculate(
                rows,
                mergeRedisOccupiedIds(input.performanceId())
        ));
    }

    private List<AvailableSeatRow> toAvailableSeatRows(final Long performanceId) {
        final List<PerformanceSeatStateRow> stateRows = seatAvailabilityReadRepository.findSeatStates(performanceId);
        if (stateRows.isEmpty()) {
            return List.of();
        }

        final PerformanceSaleSnapshot saleSnapshot = performanceSaleCatalog.getSaleSnapshot(performanceId, Set.of());

        return stateRows.stream()
                .map(row -> toAvailableSeatRow(row, saleSnapshot))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private AvailableSeatRow toAvailableSeatRow(final PerformanceSeatStateRow row, final PerformanceSaleSnapshot saleSnapshot) {
        final PerformanceSaleSnapshot.GradeInfo gradeInfo =
                saleSnapshot.gradeInfoByPerformanceGradeId().get(row.performanceGradeId());
        if (gradeInfo == null) {
            return null;
        }
        return new AvailableSeatRow(row.seatId(), row.state(), gradeInfo.gradeName(), gradeInfo.sortOrder());
    }

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> occupiedSeatIds = new HashSet<>(seatSelectionService.getSelectingSeatIds(performanceId));
        occupiedSeatIds.addAll(holdManager.getHoldingSeatIds(performanceId));
        return occupiedSeatIds;
    }
}
