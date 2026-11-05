package com.ticket.booking.seat.usecase;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.ticket.booking.exception.ShowPerformanceNotFoundException;
import com.ticket.shared.api.CacheNames;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.api.PerformanceLayoutApi;

import lombok.RequiredArgsConstructor;

/**
 * 기존 프론트의 공연 단위 좌석 배치도 계약을 현재 회차 좌석 모델에서 조합한다.
 *
 * <p><b>공연별로 10분 재사용한다({@link CacheNames#SHOW_SEAT_MAP}).</b> 예매 화면에 들어오는 사용자마다 한 번 부르는데, 요청마다 좌석 전체(대형 15,000석)를 읽고
 * 조합해 03-3 측정에서 Core 요청 시간의 72%를 썼다(gatling-test capacity-log 2026-11-05). 좌석 상태는 담지 않아 판정과 무관하다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> {@code sync = true}라 적재하는 동안 같은 공연을 찾는 요청은 기다린다. 트랜잭션이 캐시보다 먼저 열리면 기다리는 요청마다 DB connection을
 * 쥔다. 대표 회차 조회와 회차 배치도 조회가 각자 읽기 트랜잭션을 연다.
 */
@Service
@RequiredArgsConstructor
public class GetShowSeatMapUseCase {
    private final PerformanceLayoutApi performanceLayoutApi;
    private final GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase;

    public record Input(Long showId) {
        public Input {
            if (showId == null || showId <= 0) {
                throw new InvalidRequestException("showId는 양수여야 합니다.");
            }
        }
    }

    public record Output(List<SeatResponse> seats) {}

    public record SeatResponse(
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

    @Cacheable(cacheNames = CacheNames.SHOW_SEAT_MAP, key = "#input.showId()", sync = true)
    public Output execute(final Input input) {
        final Long performanceId = performanceLayoutApi
                .findRepresentativePerformanceId(input.showId())
                .orElseThrow(() -> new ShowPerformanceNotFoundException(input.showId()));
        final GetPerformanceSeatMapUseCase.Output performanceSeatMap =
                getPerformanceSeatMapUseCase.execute(new GetPerformanceSeatMapUseCase.Input(performanceId));

        return new Output(
                performanceSeatMap.seats().stream().map(this::toSeatResponse).toList());
    }

    private SeatResponse toSeatResponse(final GetPerformanceSeatMapUseCase.SeatResponse seat) {
        return new SeatResponse(
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
