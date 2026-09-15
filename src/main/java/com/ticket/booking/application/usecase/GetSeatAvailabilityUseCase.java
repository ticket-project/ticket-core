package com.ticket.booking.application.usecase;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import com.ticket.booking.application.SeatAvailabilitySnapshotReader;
import com.ticket.booking.application.port.SeatAvailabilityQueryPort.PerformanceSeatStateRow;
import com.ticket.booking.domain.hold.HoldManager;
import com.ticket.booking.domain.seat.PerformanceSeatState;
import com.ticket.booking.domain.selection.SeatSelectionService;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.api.PerformanceSaleCatalogApi;
import com.ticket.show.api.PerformanceSaleSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * 회차의 등급별 잔여석을 조회한다.
 *
 * <p><b>DB 트랜잭션을 끌고 외부 작업을 하지 않는다.</b> 예전에는 클래스 전체가 {@code @Transactional(readOnly = true)}라 Redis
 * 점유 조회와 show 모듈 호출이 booking DB connection을 쥔 채로 실행됐다 — Redis나 show가 느려지면 그만큼 connection pool이 묶인다.
 * booking local 읽기는 {@link SeatAvailabilitySnapshotReader}의 짧은 트랜잭션에서 끝내고, 그 뒤에 Redis와 show를 호출한다.
 */
@Service
@RequiredArgsConstructor
public class GetSeatAvailabilityUseCase {
    private final SeatAvailabilitySnapshotReader seatAvailabilitySnapshotReader;
    private final PerformanceSaleCatalogApi performanceSaleCatalog;
    private final HoldManager holdManager;
    private final SeatSelectionService seatSelectionService;

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

    public record Output(List<GradeAvailability> grades) {}

    /**
     * 그룹 key는 {@code performanceGradeId}다 — 변경 가능한 {@code gradeName}이 같아도 ID가 다르면 별개의 grade로 취급한다.
     */
    public record GradeAvailability(
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            BigDecimal price,
            int sortOrder,
            long availableSeats) {}

    public Output execute(Input input) {
        // 회차 존재 확인과 좌석 상태 조회를 짧은 읽기 트랜잭션에서 함께 끝낸다.
        final List<PerformanceSeatStateRow> stateRows =
                seatAvailabilitySnapshotReader.read(input.performanceId());
        if (stateRows.isEmpty()) {
            return new Output(List.of());
        }

        // 여기부터는 DB 트랜잭션 밖이다 — Redis 점유 조회와 show 표시값 조회가 connection을 쥐지 않는다.

        final PerformanceSaleSnapshot saleSnapshot =
                performanceSaleCatalog.getSaleSnapshot(input.performanceId(), Set.of());
        final Map<Long, Long> availableCountsByGrade =
                countAvailableSeatsByGrade(stateRows, mergeRedisOccupiedIds(input.performanceId()));

        final List<GradeAvailability> grades =
                availableCountsByGrade.entrySet().stream()
                        .map(
                                entry ->
                                        toGradeAvailability(
                                                entry.getKey(), entry.getValue(), saleSnapshot))
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(GradeAvailability::sortOrder))
                        .toList();

        return new Output(grades);
    }

    /**
     * 등급별 잔여석을 센다. 그룹 key는 바뀔 수 있는 gradeName이 아니라 performanceGradeId다 — 표시 이름이 같아도 ID가 다른 등급을 합치지
     * 않는다. 좌석이 하나도 남지 않은 등급도 0으로 남긴다.
     */
    private Map<Long, Long> countAvailableSeatsByGrade(
            final List<PerformanceSeatStateRow> rows, final Set<Long> redisOccupiedSeatIds) {
        if (rows.isEmpty()) {
            return Map.of();
        }

        final Map<Long, Long> availableSeatCounts = new LinkedHashMap<>();
        for (final PerformanceSeatStateRow row : rows) {
            availableSeatCounts.putIfAbsent(row.performanceGradeId(), 0L);

            if (row.state() == PerformanceSeatState.AVAILABLE
                    && !redisOccupiedSeatIds.contains(row.seatId())) {
                availableSeatCounts.computeIfPresent(
                        row.performanceGradeId(), (key, count) -> count + 1L);
            }
        }

        return availableSeatCounts;
    }

    private @Nullable GradeAvailability toGradeAvailability(
            final Long performanceGradeId,
            final Long availableSeats,
            final PerformanceSaleSnapshot saleSnapshot) {
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
                availableSeats);
    }

    /**
     * Redis가 점유로 보는 좌석을 합친다 -- 다른 회원이 고르는 중(selection)이거나 이미 선점(hold)한 좌석이다.
     *
     * <p>{@code GetSeatStatusUseCase}에 같은 모양의 method가 있다. 둘을 공통 collaborator로 묶지 않는 이유는 각자 합친 결과를
     * 쓰는 방식이 다르기 때문이다 -- 여기서는 등급별 개수를 세고, 저기서는 좌석마다 상태를 매긴다. <b>다만 "무엇을 점유로 보는가"는 같아야 하므로 모양을 일부러
     * 똑같이 맞춰 둔다.</b> 한쪽에 조건이 붙으면 다른 쪽도 함께 본다.
     */
    private Set<Long> mergeRedisOccupiedIds(final Long performanceId) {
        final Set<Long> selectingSeatIds = seatSelectionService.getSelectingSeatIds(performanceId);
        final Set<Long> holdingSeatIds = holdManager.getHoldingSeatIds(performanceId);

        final Set<Long> occupiedSeatIds =
                HashSet.newHashSet(selectingSeatIds.size() + holdingSeatIds.size());
        occupiedSeatIds.addAll(selectingSeatIds);
        occupiedSeatIds.addAll(holdingSeatIds);
        return occupiedSeatIds;
    }
}
