package com.ticket.core.api.controller.docs;

import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.performance.query.GetBookingEntryUseCase;
import com.ticket.core.domain.performance.query.GetPerformanceScheduleListUseCase;
import com.ticket.core.domain.performance.query.GetPerformanceSummaryUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatAvailabilityUseCase;
import com.ticket.core.domain.performanceseat.query.GetSeatStatusUseCase;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "Performance", description = "Performance APIs")
public interface PerformanceControllerDocs {

    @Operation(summary = "Get performance summary")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetPerformanceSummaryUseCase.Output> getPerformanceSummary(
            @Parameter(description = "Performance ID", example = "1", required = true) Long performanceId
    );

    @Operation(summary = "Get performance schedules")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetPerformanceScheduleListUseCase.Output> getPerformanceSchedules(
            @Parameter(description = "Performance ID", example = "1", required = true) Long performanceId
    );

    @Operation(
            summary = "Decide booking entry route",
            description = "Ticket Server checks the performance queue policy and returns DIRECT or QUEUE."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetBookingEntryUseCase.Output> getBookingEntry(
            @Parameter(description = "Performance ID", example = "1", required = true) Long performanceId
    );

    @Operation(
            summary = "Get seat availability by grade",
            description = "Admission token is required only when the performance queue policy requires queue admission."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetSeatAvailabilityUseCase.Output> getSeatAvailability(
            @Parameter(description = "Performance ID", example = "1", required = true) Long performanceId,
            @Parameter(hidden = true) MemberPrincipal memberPrincipal,
            @Parameter(hidden = true) HttpServletRequest servletRequest
    );

    @Operation(
            summary = "Get seat status",
            description = "Admission token is required only when the performance queue policy requires queue admission."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success")
    })
    ApiResponse<GetSeatStatusUseCase.Output> getSeatStatus(
            @Parameter(description = "Performance ID", example = "1", required = true) Long performanceId,
            @Parameter(hidden = true) MemberPrincipal memberPrincipal,
            @Parameter(hidden = true) HttpServletRequest servletRequest
    );
}