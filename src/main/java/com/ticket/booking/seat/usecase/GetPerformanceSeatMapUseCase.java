package com.ticket.booking.seat.usecase;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.booking.seat.domain.PerformanceSeat;
import com.ticket.booking.seat.domain.PerformanceSeatRepository;
import com.ticket.show.api.PerformanceLayoutSnapshot;
import com.ticket.show.api.PerformanceVenueLayoutCatalogApi;

import lombok.RequiredArgsConstructor;

/**
 * 회차 정적 seat-map을 조합한다. Venue 배치·물리 Seat 좌표·PerformanceGrade 표시값은 show {@link
 * PerformanceVenueLayoutCatalogApi}에서, 이 회차에 실제로 판매 편성된 좌석(PerformanceSeat)과 확정 가격은 booking local에서
 * 각각 한 번씩만 조회해 N+1 없이 고정된 query 수로 조합한다.
 *
 * <p>Performance에 판매 편성되지 않은 물리 Seat는 {@link PerformanceSeatRepository}에 아예 나타나지 않으므로 응답에도 포함되지
 * 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSeatMapUseCase {
    private final PerformanceVenueLayoutCatalogApi performanceVenueLayoutCatalog;
    private final PerformanceSeatRepository performanceSeatRepository;

    public record Input(Long performanceId) {
        public Input {
            performanceId = requirePositiveId(performanceId, "performanceId");
        }
    }

    public record Output(VenueView venue, List<SeatMapEntry> seats) {}

    public record VenueView(
            @Nullable Long venueId,
            @Nullable String venueName,
            int viewBoxWidth,
            int viewBoxHeight,
            double seatDiameter) {}

    public record SeatMapEntry(
            Long performanceSeatId,
            Long seatId,
            int floor,
            String section,
            String row,
            String col,
            double x,
            double y,
            Long performanceGradeId,
            String gradeCode,
            String gradeName,
            BigDecimal price) {}

    public Output execute(final Input input) {
        final PerformanceLayoutSnapshot layout =
                performanceVenueLayoutCatalog.getVenueLayout(input.performanceId());
        final List<PerformanceSeat> performanceSeats =
                performanceSeatRepository.findAllByPerformanceId(input.performanceId());

        final List<SeatMapEntry> seats =
                performanceSeats.stream()
                        .map(performanceSeat -> toSeatMapEntry(performanceSeat, layout))
                        .filter(Objects::nonNull)
                        .toList();

        return new Output(toVenueView(layout), seats);
    }

    private VenueView toVenueView(final PerformanceLayoutSnapshot layout) {
        return new VenueView(
                layout.venueId(),
                layout.venueName(),
                layout.viewBoxWidth(),
                layout.viewBoxHeight(),
                layout.seatDiameter());
    }

    /** show 쪽 좌표·등급 표시값이 이 좌석과 매칭되지 않으면(데이터 불일치) 조용히 제외한다 — 어떤 오류로 다룰지는 이 조합 시점에서 판정하지 않는다. */
    private @Nullable SeatMapEntry toSeatMapEntry(
            final PerformanceSeat performanceSeat, final PerformanceLayoutSnapshot layout) {
        final PerformanceLayoutSnapshot.SeatLayout seatLayout =
                layout.seatLayoutBySeatId().get(performanceSeat.getSeatId());
        final PerformanceLayoutSnapshot.GradeLayout gradeLayout =
                layout.gradeLayoutByPerformanceGradeId()
                        .get(performanceSeat.getPerformanceGradeId());
        if (seatLayout == null || gradeLayout == null) {
            return null;
        }
        return new SeatMapEntry(
                performanceSeat.getId(),
                performanceSeat.getSeatId(),
                seatLayout.floor(),
                seatLayout.section(),
                seatLayout.rowNo(),
                seatLayout.seatNo(),
                seatLayout.x(),
                seatLayout.y(),
                performanceSeat.getPerformanceGradeId(),
                gradeLayout.gradeCode(),
                gradeLayout.gradeName(),
                performanceSeat.getUnitPrice());
    }
}
