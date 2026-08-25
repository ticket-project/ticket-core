package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.PerformanceControllerDocs;
import com.ticket.core.config.admission.AdmissionTokenService;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.app.performance.query.GetPerformanceScheduleListUseCase;
import com.ticket.core.app.performance.query.GetPerformanceSummaryUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatAvailabilityUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatStatusUseCase;
import com.ticket.core.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
public class PerformanceController implements PerformanceControllerDocs {

    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase;
    private final GetSeatStatusUseCase getSeatStatusUseCase;
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
            @RequestHeader(value = AdmissionTokenService.HEADER, required = false) final String admissionToken,
            final MemberPrincipal memberPrincipal
    ) {
        final GetSeatStatusUseCase.Input input = new GetSeatStatusUseCase.Input(
                performanceId,
                memberPrincipal.getMemberId(),
                admissionToken
        );
        return ApiResponse.success(getSeatStatusUseCase.execute(input));
    }
}
