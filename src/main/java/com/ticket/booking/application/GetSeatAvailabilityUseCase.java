package com.ticket.booking.application;

import com.ticket.booking.domain.HoldManager;
import com.ticket.booking.application.SeatAvailabilityReadRepository.PerformanceSeatStateRow;
import com.ticket.booking.domain.PerformanceSalesPolicyRepository;
import com.ticket.booking.domain.SeatSelectionService;
import com.ticket.show.PerformanceSaleCatalog;
import com.ticket.show.PerformanceSaleSnapshot;
import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSeatAvailabilityUseCase {

    private final PerformanceSalesPolicyRepository performanceSalesPolicyRepository;
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

    /**
     * 그룹 key는 {@code performanceGradeId}다 — 변경 가능한 {@code gradeName}이 같아도 ID가 다르면
     * 별개의 grade로 취급한다.
     */
    public record GradeAvailability(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            BigDecimal price,
            int sortOrder,
            long availableSeats
    ) {}

    public Output execute(Input input) {
        // 회차 판매 정책 조회는 회차 존재 확인을 겸한다. 접수 기간 차단은 기존과 같이 여기서 새로
        // 추가하지 않는다 — 잔여석 조회는 접수 종료 후에도 조회 가능해야 한다.
        performanceSalesPolicyRepository.findById(input.performanceId())
                .orElseThrow(() -> new NotFoundException(
                        "회차 판매 정책을 찾을 수 없습니다. id=" + input.performanceId()));

        final List<PerformanceSeatStateRow> stateRows = seatAvailabilityReadRepository.findSeatStates(input.performanceId());
        if (stateRows.isEmpty()) {
            return new Output(List.of());
        }

        final PerformanceSaleSnapshot saleSnapshot = performanceSaleCatalog.getSaleSnapshot(input.performanceId(), Set.of());
        final Map<Long, Long> availableCountsByGrade =
                seatAvailabilityCalculator.calculate(stateRows, mergeRedisOccupiedIds(input.performanceId()));

        final List<GradeAvailability> grades = availableCountsByGrade.entrySet().stream()
                .map(entry -> toGradeAvailability(entry.getKey(), entry.getValue(), saleSnapshot))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(GradeAvailability::sortOrder))
                .toList();

        return new Output(grades);
    }

    private GradeAvailability toGradeAvailability(
            final Long performanceGradeId,
            final Long availableSeats,
            final PerformanceSaleSnapshot saleSnapshot
    ) {
        final PerformanceSaleSnapshot.GradeInfo gradeInfo =
                saleSnapshot.gradeInfoByPerformanceGradeId().get(performanceGradeId);
        if (gradeInfo == null) {
            return null;
        }
        return new GradeAvailability(
                performanceGradeId,
                gradeInfo.gradeCode(),
                gradeInfo.gradeName(),
                gradeInfo.price(),
                gradeInfo.sortOrder(),
                availableSeats
        );
    }

    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> occupiedSeatIds = new HashSet<>(seatSelectionService.getSelectingSeatIds(performanceId));
        occupiedSeatIds.addAll(holdManager.getHoldingSeatIds(performanceId));
        return occupiedSeatIds;
    }
}
