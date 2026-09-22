package com.ticket.booking.seat.usecase;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.exception.NotFoundException;
import com.ticket.show.api.PerformanceVenueLayoutCatalogApi;

import lombok.RequiredArgsConstructor;

/** 기존 프론트의 공연 단위 좌석 배치도 계약을 현재 회차 좌석 모델에서 조합한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowSeatMapUseCase {
    private final PerformanceVenueLayoutCatalogApi performanceVenueLayoutCatalog;
    private final GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase;

    public record Input(Long showId) {
        public Input {
            if (showId == null || showId <= 0) {
                throw new InvalidRequestException("showId는 양수여야 합니다.");
            }
        }
    }

    public record Output(List<SeatMapEntry> seats) {}

    public record SeatMapEntry(
            Long seatId,
            int floor,
            String section,
            String row,
            String col,
            double x,
            double y,
            String gradeCode,
            String gradeName,
            BigDecimal price) {}

    public Output execute(final Input input) {
        final Long performanceId = performanceVenueLayoutCatalog
                .findRepresentativePerformanceId(input.showId())
                .orElseThrow(() -> new NotFoundException("공연 회차를 찾을 수 없습니다. showId=" + input.showId()));
        final GetPerformanceSeatMapUseCase.Output performanceSeatMap =
                getPerformanceSeatMapUseCase.execute(new GetPerformanceSeatMapUseCase.Input(performanceId));

        return new Output(
                performanceSeatMap.seats().stream().map(this::toSeatMapEntry).toList());
    }

    private SeatMapEntry toSeatMapEntry(final GetPerformanceSeatMapUseCase.SeatMapEntry seat) {
        return new SeatMapEntry(
                seat.seatId(),
                seat.floor(),
                seat.section(),
                seat.row(),
                seat.col(),
                seat.x(),
                seat.y(),
                seat.gradeCode(),
                seat.gradeName(),
                seat.price());
    }
}
