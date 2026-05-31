package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.SeatSelectionControllerDocs;
import com.ticket.core.config.admission.AdmissionTokenValidator;
import com.ticket.support.passport.Passport;
import com.ticket.core.domain.performanceseat.command.DeselectAllSeatsUseCase;
import com.ticket.core.domain.performanceseat.command.DeselectSeatUseCase;
import com.ticket.core.domain.performanceseat.command.SelectSeatUseCase;
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
    private final AdmissionTokenValidator admissionTokenValidator;

    @Override
    @PostMapping("/{seatId}/select")
    public ApiResponse<Void> selectSeat(
            @PathVariable final Long performanceId,
            @PathVariable final Long seatId,
            @RequestHeader(value = AdmissionTokenValidator.HEADER, required = false) final String admissionToken,
            final Passport memberPrincipal
    ) {
        admissionTokenValidator.validate(performanceId, admissionToken);
        selectSeatUseCase.execute(new SelectSeatUseCase.Input(performanceId, seatId, memberPrincipal.memberId()));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/{seatId}/select")
    public ApiResponse<Void> deselectSeat(
            @PathVariable final Long performanceId,
            @PathVariable final Long seatId,
            final Passport memberPrincipal
    ) {
        deselectSeatUseCase.execute(new DeselectSeatUseCase.Input(performanceId, seatId, memberPrincipal.memberId()));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/select")
    public ApiResponse<Void> deselectAllSeats(
            @PathVariable final Long performanceId,
            final Passport memberPrincipal
    ) {
        deselectAllSeatsUseCase.execute(new DeselectAllSeatsUseCase.Input(performanceId, memberPrincipal.memberId()));
        return ApiResponse.success();
    }
}
