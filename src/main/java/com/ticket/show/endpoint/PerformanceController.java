package com.ticket.show.endpoint;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.shared.web.ApiResponse;
import com.ticket.show.usecase.GetPerformanceSummaryUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
@Tag(name = "회차(Performance)", description = "회차 요약·좌석 조회 API")
public class PerformanceController {
    private final GetPerformanceSummaryUseCase getPerformanceSummaryUseCase;

    @Operation(summary = "회차 요약 조회")
    @GetMapping("/{performanceId}/summary")
    public ApiResponse<GetPerformanceSummaryUseCase.Output> getPerformanceSummary(
            @Parameter(description = "회차 ID", example = "1") @PathVariable @Positive final Long performanceId) {
        final GetPerformanceSummaryUseCase.Input input = new GetPerformanceSummaryUseCase.Input(performanceId);
        return ApiResponse.success(getPerformanceSummaryUseCase.execute(input));
    }
}
