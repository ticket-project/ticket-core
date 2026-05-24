package com.ticket.core.api.controller.docs;

import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "좌석 선택", description = "좌석 선택/해제 API (실시간 알림은 WebSocket 구독)")
public interface SeatSelectionControllerDocs {

    @Operation(
            summary = "좌석 선택",
            description = """
                    특정 좌석을 임시 선택 상태로 변경합니다.
                    Redis에 선택 상태를 저장하고, 성공하면 WebSocket으로 SELECTED 이벤트를 전파합니다.
                    선택 상태는 5분 뒤 자동 만료됩니다.
                    이 선택 상태는 화면 UX 보조용이며, HOLD 생성의 필수 선행 조건은 아닙니다.
                    좌석은 실제로 존재하고 해당 회차 소속이며 DB 기준 예매 가능한 상태여야 선택할 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌석 선택 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 선택된 좌석")
    })
    ApiResponse<Void> selectSeat(
            @Parameter(description = "회차 ID", example = "1", required = true) Long performanceId,
            @Parameter(description = "좌석 ID", example = "42", required = true) Long seatId,
            @Parameter(hidden = true) MemberPrincipal memberPrincipal,
            @Parameter(hidden = true) HttpServletRequest servletRequest
    );

    @Operation(
            summary = "좌석 선택 해제",
            description = """
                    특정 좌석의 선택 상태를 해제합니다.
                    본인이 선택한 좌석만 해제할 수 있으며, 성공하면 WebSocket으로 DESELECTED 이벤트를 전파합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌석 선택 해제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인이 선택한 좌석이 아님")
    })
    ApiResponse<Void> deselectSeat(
            @Parameter(description = "회차 ID", example = "1", required = true) Long performanceId,
            @Parameter(description = "좌석 ID", example = "42", required = true) Long seatId,
            @Parameter(hidden = true) MemberPrincipal memberPrincipal,
            @Parameter(hidden = true) HttpServletRequest servletRequest
    );

    @Operation(
            summary = "내 선택 좌석 전체 해제",
            description = """
                    현재 사용자가 해당 공연에서 선택한 좌석을 모두 해제합니다.
                    브라우저 종료나 페이지 이탈 직전에 호출하는 정리용 API로 사용할 수 있습니다.
                    해제된 각 좌석에 대해 WebSocket으로 DESELECTED 이벤트를 전파합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 선택 좌석 전체 해제 성공")
    })
    ApiResponse<Void> deselectAllSeats(
            @Parameter(description = "회차 ID", example = "1", required = true) Long performanceId,
            @Parameter(hidden = true) MemberPrincipal memberPrincipal,
            @Parameter(hidden = true) HttpServletRequest servletRequest
    );
}
