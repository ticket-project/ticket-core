package com.ticket.booking.salespolicy.endpoint;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.salespolicy.usecase.GetPerformanceBookingModeUseCase;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/booking/performances")
@RequiredArgsConstructor
@Tag(name = "예매 방식(Booking Mode)", description = "회차 예매 방식 조회 API")
public class PerformanceBookingModeController {
    private final GetPerformanceBookingModeUseCase getPerformanceBookingModeUseCase;

    @Operation(summary = "회차 예매 방식 조회", description = """
            회차의 예매 접수 상태와 예매 방식(DIRECT/QUEUE/UNAVAILABLE)을 인증 없이 조회한다.
            안내용 조회이므로 실제 좌석 선택·상태·주문 API는 실행 시점에 정책을 다시 검사한다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "회차 판매 정책이 구성되지 않음")
    })
    @GetMapping("/{performanceId}/booking-mode")
    public ApiResponse<GetPerformanceBookingModeUseCase.Output> getPerformanceBookingMode(
            @Parameter(description = "회차 ID", example = "1") @PathVariable @Positive final Long performanceId) {
        final GetPerformanceBookingModeUseCase.Input input = new GetPerformanceBookingModeUseCase.Input(performanceId);
        return ApiResponse.success(getPerformanceBookingModeUseCase.execute(input));
    }
}
