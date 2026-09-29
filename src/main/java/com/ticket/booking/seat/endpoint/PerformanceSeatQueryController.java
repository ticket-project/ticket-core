package com.ticket.booking.seat.endpoint;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.seat.usecase.GetPerformanceSeatMapUseCase;
import com.ticket.booking.seat.usecase.GetSeatAvailabilityUseCase;
import com.ticket.booking.seat.usecase.GetSeatStatusUseCase;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * {@code .../seat-map}은 회차 정적 seat-map(Venue 배치·좌석 좌표·등급·가격)을 읽는다. {@code .../seats/availability},
 * {@code .../seats/status}는 회차별 판매 상태(PerformanceSeat)와 Redis selection/hold를 함께 읽는다.
 *
 * <p>{@code PerformanceSeat}와 Redis selection/hold 상태는 booking이 소유하는 데이터라, 이 세 엔드포인트는 순수 show/공연 요약 데이터만 다루는
 * {@code com.ticket.show.endpoint.PerformanceController}가 아니라 booking 소유인 이
 * {@code com.ticket.booking.seat.endpoint.PerformanceSeatQueryController}에 둔다. URL·JSON 계약은 기존과 동일하다.
 */
@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
@Tag(name = "Performance", description = "회차 요약·좌석 조회 API")
public class PerformanceSeatQueryController {
    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase;
    private final GetSeatStatusUseCase getSeatStatusUseCase;
    private final GetPerformanceSeatMapUseCase getPerformanceSeatMapUseCase;

    @Operation(
            summary = "Get performance seat map",
            description =
                    "Returns the static seat map (venue layout, seat coordinates, grade and price) for a performance.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")})
    @GetMapping("/{performanceId}/seat-map")
    public ApiResponse<GetPerformanceSeatMapUseCase.Output> getSeatMap(
            @Parameter(description = "Performance ID", example = "1") @PathVariable @Positive
                    final Long performanceId) {
        final GetPerformanceSeatMapUseCase.Input input = new GetPerformanceSeatMapUseCase.Input(performanceId);
        return ApiResponse.success(getPerformanceSeatMapUseCase.execute(input));
    }

    @Operation(summary = "Get seat availability by grade", description = "Returns available seat counts by grade.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")})
    @GetMapping("/{performanceId}/seats/availability")
    public ApiResponse<GetSeatAvailabilityUseCase.Output> getSeatAvailability(
            @Parameter(description = "Performance ID", example = "1") @PathVariable @Positive
                    final Long performanceId) {
        final GetSeatAvailabilityUseCase.Input input = new GetSeatAvailabilityUseCase.Input(performanceId);
        return ApiResponse.success(getSeatAvailabilityUseCase.execute(input));
    }

    @Operation(summary = "Get seat status", description = "Returns current seat status for a performance.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")})
    @GetMapping("/{performanceId}/seats/status")
    public ApiResponse<GetSeatStatusUseCase.Output> getSeatStatus(
            @Parameter(description = "Performance ID", example = "1") @PathVariable @Positive final Long performanceId,
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @Parameter(description = "Admission token issued by Queue Server")
                    @RequestHeader(value = "X-Admission-Token", required = false)
                    final String admissionToken,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        final GetSeatStatusUseCase.Input input =
                new GetSeatStatusUseCase.Input(performanceId, member.memberId(), admissionToken);
        return ApiResponse.success(getSeatStatusUseCase.execute(input));
    }
}
