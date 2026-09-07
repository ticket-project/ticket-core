package com.ticket.booking.application.performanceseat.query;

import com.ticket.booking.application.performanceseat.query.PerformanceSeatMapReadRepository.PerformanceSeatMapRow;
import com.ticket.show.PerformanceVenueLayout;
import com.ticket.show.PerformanceVenueLayoutCatalog;
import com.ticket.error.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 회차 정적 seat-map을 조합한다. Venue 배치·물리 Seat 좌표·PerformanceGrade 표시값은 show
 * {@link PerformanceVenueLayoutCatalog}에서, 이 회차에 실제로 판매 편성된 좌석(PerformanceSeat)과
 * 확정 가격은 booking local에서 각각 한 번씩만 조회해 N+1 없이 고정된 query 수로 조합한다.
 *
 * <p>Performance에 판매 편성되지 않은 물리 Seat는 {@link PerformanceSeatMapReadRepository}에 아예
 * 나타나지 않으므로 응답에도 포함되지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetPerformanceSeatMapUseCase {

    private final PerformanceVenueLayoutCatalog performanceVenueLayoutCatalog;
    private final PerformanceSeatMapReadRepository performanceSeatMapReadRepository;

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

    public record Output(VenueView venue, List<SeatMapEntry> seats) {}

    public record VenueView(
            Long venueId,
            String venueName,
            int viewBoxWidth,
            int viewBoxHeight,
            double seatDiameter
    ) {}

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
            BigDecimal price
    ) {}

    public Output execute(final Input input) {
        final PerformanceVenueLayout layout = performanceVenueLayoutCatalog.getVenueLayout(input.performanceId());
        final List<PerformanceSeatMapRow> rows =
                performanceSeatMapReadRepository.findAllByPerformanceId(input.performanceId());

        final List<SeatMapEntry> seats = rows.stream()
                .map(row -> toSeatMapEntry(row, layout))
                .filter(Objects::nonNull)
                .toList();

        return new Output(toVenueView(layout), seats);
    }

    private VenueView toVenueView(final PerformanceVenueLayout layout) {
        return new VenueView(
                layout.venueId(),
                layout.venueName(),
                layout.viewBoxWidth(),
                layout.viewBoxHeight(),
                layout.seatDiameter()
        );
    }

    /**
     * show 쪽 좌표·등급 표시값이 이 좌석과 매칭되지 않으면(데이터 불일치) 조용히 제외한다 — 어떤
     * 오류로 다룰지는 이 조합 시점에서 판정하지 않는다.
     */
    private SeatMapEntry toSeatMapEntry(final PerformanceSeatMapRow row, final PerformanceVenueLayout layout) {
        final PerformanceVenueLayout.SeatLayout seatLayout = layout.seatLayoutBySeatId().get(row.seatId());
        final PerformanceVenueLayout.GradeLayout gradeLayout =
                layout.gradeLayoutByPerformanceGradeId().get(row.performanceGradeId());
        if (seatLayout == null || gradeLayout == null) {
            return null;
        }
        return new SeatMapEntry(
                row.performanceSeatId(),
                row.seatId(),
                seatLayout.floor(),
                seatLayout.section(),
                seatLayout.rowNo(),
                seatLayout.seatNo(),
                seatLayout.x(),
                seatLayout.y(),
                row.performanceGradeId(),
                gradeLayout.gradeCode(),
                gradeLayout.gradeName(),
                row.unitPrice()
        );
    }
}
