package com.ticket.show.web;

import com.ticket.show.web.docs.PerformanceControllerDocs;
import com.ticket.show.application.usecase.GetPerformanceScheduleListUseCase;
import com.ticket.show.application.usecase.GetPerformanceSummaryUseCase;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
public class PerformanceController implements PerformanceControllerDocs {

    private final GetPerformanceSummaryUseCase getPerformanceSummaryUseCase;
    private final GetPerformanceScheduleListUseCase getPerformanceScheduleListUseCase;

    @Override
    @GetMapping("/{performanceId}/summary")
    public ApiResponse<GetPerformanceSummaryUseCase.Output> getPerformanceSummary(
            @PathVariable final Long performanceId
    ) {
        final GetPerformanceSummaryUseCase.Input input = new GetPerformanceSummaryUseCase.Input(performanceId);
        return ApiResponse.success(getPerformanceSummaryUseCase.execute(input));
    }

    @Override
    @GetMapping("/{performanceId}/schedules")
    public ApiResponse<GetPerformanceScheduleListUseCase.Output> getPerformanceSchedules(
            @PathVariable final Long performanceId
    ) {
        final GetPerformanceScheduleListUseCase.Input input = new GetPerformanceScheduleListUseCase.Input(performanceId);
        return ApiResponse.success(getPerformanceScheduleListUseCase.execute(input));
    }
}
