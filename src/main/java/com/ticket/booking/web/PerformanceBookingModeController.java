package com.ticket.booking.web;

import com.ticket.booking.application.usecase.GetPerformanceBookingModeUseCase;
import com.ticket.booking.web.docs.PerformanceBookingModeControllerDocs;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/booking/performances")
@RequiredArgsConstructor
public class PerformanceBookingModeController implements PerformanceBookingModeControllerDocs {

    private final GetPerformanceBookingModeUseCase getPerformanceBookingModeUseCase;

    @Override
    @GetMapping("/{performanceId}/booking-mode")
    public ApiResponse<GetPerformanceBookingModeUseCase.Output> getPerformanceBookingMode(
            @PathVariable final Long performanceId
    ) {
        final GetPerformanceBookingModeUseCase.Input input = new GetPerformanceBookingModeUseCase.Input(performanceId);
        return ApiResponse.success(getPerformanceBookingModeUseCase.execute(input));
    }
}
