package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.SeatSelectionControllerDocs;
import com.ticket.core.api.AdmissionHeaders;
import com.ticket.core.config.security.AuthenticatedMember;
import com.ticket.core.app.performanceseat.command.DeselectAllSeatsUseCase;
import com.ticket.core.app.performanceseat.command.DeselectSeatUseCase;
import com.ticket.core.app.performanceseat.command.SelectSeatUseCase;
import com.ticket.core.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/performances/{performanceId}/seats")
@RequiredArgsConstructor
public class SeatSelectionController implements SeatSelectionControllerDocs {

    private final SelectSeatUseCase selectSeatUseCase;
    private final DeselectSeatUseCase deselectSeatUseCase;
    private final DeselectAllSeatsUseCase deselectAllSeatsUseCase;

    @Override
    @PostMapping("/{seatId}/select")
    public ApiResponse<Void> selectSeat(
            @PathVariable final Long performanceId,
            @PathVariable final Long seatId,
            @RequestHeader(value = AdmissionHeaders.ADMISSION_TOKEN, required = false) final String admissionToken,
            final AuthenticatedMember member
    ) {
        selectSeatUseCase.execute(new SelectSeatUseCase.Input(
                performanceId,
                seatId,
                member.memberId(),
                admissionToken
        ));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/{seatId}/select")
    public ApiResponse<Void> deselectSeat(
            @PathVariable final Long performanceId,
            @PathVariable final Long seatId,
            final AuthenticatedMember member
    ) {
        deselectSeatUseCase.execute(new DeselectSeatUseCase.Input(performanceId, seatId, member.memberId()));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/select")
    public ApiResponse<Void> deselectAllSeats(
            @PathVariable final Long performanceId,
            final AuthenticatedMember member
    ) {
        deselectAllSeatsUseCase.execute(new DeselectAllSeatsUseCase.Input(performanceId, member.memberId()));
        return ApiResponse.success();
    }
}
