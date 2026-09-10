package com.ticket.booking.seat.web;

import com.ticket.booking.seat.web.docs.PerformanceSeatQueryControllerDocs;
import com.ticket.member.AuthenticatedMember;
import com.ticket.booking.seat.application.usecase.GetPerformanceSeatMapUseCase;
import com.ticket.booking.seat.application.usecase.GetSeatAvailabilityUseCase;
import com.ticket.booking.seat.application.usecase.GetSeatStatusUseCase;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code .../seat-map}은 회차 정적 seat-map(Venue 배치·좌석 좌표·등급·가격)을 읽는다.
 * {@code .../seats/availability}, {@code .../seats/status}는 회차별 판매 상태(PerformanceSeat)와
 * Redis selection/hold를 함께 읽는다.
 *
 * <p>{@code PerformanceSeat}와 Redis selection/hold 상태는 booking이 소유하는 데이터라, 이 세
 * 엔드포인트는 순수 show/공연 요약 데이터만 다루는 {@code com.ticket.show.web.PerformanceController}가
 * 아니라 booking 소유인 이 {@code booking.seat.web.PerformanceSeatQueryController}에 둔다. URL·JSON
 * 계약은 기존과 동일하다.
 */
@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
public class PerformanceSeatQueryController implements PerformanceSeatQueryControllerDocs {

    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase;
    private final GetSeatStatusUseCase getSeatStatusUseCase;
    private final GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase;

    @Override
    @GetMapping("/{performanceId}/seat-map")
    public ApiResponse<GetPerformanceSeatMapUseCase.Output> getSeatMap(
            @PathVariable final Long performanceId
    ) {
        final GetPerformanceSeatMapUseCase.Input input = new GetPerformanceSeatMapUseCase.Input(performanceId);
        return ApiResponse.success(getPerformanceSeatMapUseCase.execute(input));
    }

    @Override
    @GetMapping("/{performanceId}/seats/availability")
    public ApiResponse<GetSeatAvailabilityUseCase.Output> getSeatAvailability(
            @PathVariable final Long performanceId
    ) {
        final GetSeatAvailabilityUseCase.Input input = new GetSeatAvailabilityUseCase.Input(performanceId);
        return ApiResponse.success(getSeatAvailabilityUseCase.execute(input));
    }

    @Override
    @GetMapping("/{performanceId}/seats/status")
    public ApiResponse<GetSeatStatusUseCase.Output> getSeatStatus(
            @PathVariable final Long performanceId,
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @RequestHeader(value = "X-Admission-Token", required = false) final String admissionToken,
            final AuthenticatedMember member
    ) {
        final GetSeatStatusUseCase.Input input = new GetSeatStatusUseCase.Input(
                performanceId,
                member.memberId(),
                admissionToken
        );
        return ApiResponse.success(getSeatStatusUseCase.execute(input));
    }
}
