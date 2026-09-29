package com.ticket.booking.selection.endpoint;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.selection.usecase.DeselectAllSeatsUseCase;
import com.ticket.booking.selection.usecase.DeselectSeatUseCase;
import com.ticket.booking.selection.usecase.SelectSeatUseCase;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/performances/{performanceId}/seats")
@RequiredArgsConstructor
@Tag(name = "좌석 선택", description = "좌석 선택/해제 API (실시간 알림은 WebSocket 구독)")
public class SeatSelectionController {
    private final SelectSeatUseCase selectSeatUseCase;
    private final DeselectSeatUseCase deselectSeatUseCase;
    private final DeselectAllSeatsUseCase deselectAllSeatsUseCase;

    @Operation(summary = "좌석 선택", description = """
            특정 좌석을 임시 선택 상태로 변경합니다.
            Redis에 선택 상태를 저장하고, 성공하면 WebSocket으로 SELECTED 이벤트를 전파합니다.
            선택 상태는 5분 뒤 자동 만료됩니다.
            주문은 본인이 선택 중인 좌석으로만 시작할 수 있습니다(ADR 0021).
            좌석은 실제로 존재하고 해당 회차 소속이며 DB 기준 예매 가능한 상태여야 선택할 수 있습니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌석 선택 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 선택된 좌석")
    })
    @PostMapping("/{seatId}/select")
    public ApiResponse<Void> selectSeat(
            @Parameter(description = "회차 ID", example = "1") @PathVariable @Positive final Long performanceId,
            @Parameter(description = "좌석 ID", example = "42") @PathVariable @Positive final Long seatId,
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @Parameter(description = "Queue Server가 발급한 admission token")
                    @RequestHeader(value = "X-Admission-Token", required = false)
                    final String admissionToken,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        selectSeatUseCase.execute(
                new SelectSeatUseCase.Input(performanceId, seatId, member.memberId(), admissionToken));
        return ApiResponse.success();
    }

    @Operation(summary = "좌석 선택 해제", description = """
            특정 좌석의 선택 상태를 해제합니다.
            본인이 선택한 좌석만 해제할 수 있으며, 성공하면 WebSocket으로 DESELECTED 이벤트를 전파합니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌석 선택 해제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인이 선택한 좌석이 아님")
    })
    @DeleteMapping("/{seatId}/select")
    public ApiResponse<Void> deselectSeat(
            @Parameter(description = "회차 ID", example = "1") @PathVariable @Positive final Long performanceId,
            @Parameter(description = "좌석 ID", example = "42") @PathVariable @Positive final Long seatId,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        deselectSeatUseCase.execute(new DeselectSeatUseCase.Input(performanceId, seatId, member.memberId()));
        return ApiResponse.success();
    }

    @Operation(summary = "내 선택 좌석 전체 해제", description = """
            현재 사용자가 해당 공연에서 선택한 좌석을 모두 해제합니다.
            브라우저 종료나 페이지 이탈 직전에 호출하는 정리용 API로 사용할 수 있습니다.
            해제된 각 좌석에 대해 WebSocket으로 DESELECTED 이벤트를 전파합니다.
            """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 선택 좌석 전체 해제 성공")
    })
    @DeleteMapping("/select")
    public ApiResponse<Void> deselectAllSeats(
            @Parameter(description = "회차 ID", example = "1") @PathVariable @Positive final Long performanceId,
            @Parameter(hidden = true) final AuthenticatedMember member) {
        deselectAllSeatsUseCase.execute(new DeselectAllSeatsUseCase.Input(performanceId, member.memberId()));
        return ApiResponse.success();
    }
}
