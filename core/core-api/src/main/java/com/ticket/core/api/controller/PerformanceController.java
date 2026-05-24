package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.PerformanceControllerDocs;
import com.ticket.core.config.AdmissionTokenValidator;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.performance.query.GetBookingEntryUseCase;
import com.ticket.core.domain.performance.query.GetPerformanceScheduleListUseCase;
import com.ticket.core.domain.performance.query.GetPerformanceSummaryUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatAvailabilityUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatStatusUseCase;
import com.ticket.core.support.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
public class PerformanceController implements PerformanceControllerDocs {

    private final GetSeatAvailabilityUseCase getSeatAvailabilityUseCase;
    private final GetSeatStatusUseCase getSeatStatusUseCase;
    private final GetPerformanceSummaryUseCase getPerformanceSummaryUseCase;
    private final GetPerformanceScheduleListUseCase getPerformanceScheduleListUseCase;
    private final GetBookingEntryUseCase getBookingEntryUseCase;
    private final AdmissionTokenValidator admissionTokenValidator;

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
    @GetMapping("/{performanceId}/booking-entry")
    public ApiResponse<GetBookingEntryUseCase.Output> getBookingEntry(
            @PathVariable final Long performanceId
    ) {
        final GetBookingEntryUseCase.Input input = new GetBookingEntryUseCase.Input(performanceId);
        return ApiResponse.success(getBookingEntryUseCase.execute(input));
    }

    @Override
    @GetMapping("/{performanceId}/seats/availability")
    public ApiResponse<GetSeatAvailabilityUseCase.Output> getSeatAvailability(
            @PathVariable final Long performanceId,
            final MemberPrincipal memberPrincipal,
            final HttpServletRequest servletRequest
    ) {
        admissionTokenValidator.verify(servletRequest, memberPrincipal.getMemberId(), performanceId);
        final GetSeatAvailabilityUseCase.Input input = new GetSeatAvailabilityUseCase.Input(performanceId);
        return ApiResponse.success(getSeatAvailabilityUseCase.execute(input));
    }

    @Override
    @GetMapping("/{performanceId}/seats/status")
    public ApiResponse<GetSeatStatusUseCase.Output> getSeatStatus(
            @PathVariable final Long performanceId,
            final MemberPrincipal memberPrincipal,
            final HttpServletRequest servletRequest
    ) {
        admissionTokenValidator.verify(servletRequest, memberPrincipal.getMemberId(), performanceId);
        final GetSeatStatusUseCase.Input input = new GetSeatStatusUseCase.Input(performanceId);
        return ApiResponse.success(getSeatStatusUseCase.execute(input));
    }
}