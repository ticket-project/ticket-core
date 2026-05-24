package com.ticket.core.api.controller;

import com.ticket.core.api.controller.docs.SeatSelectionControllerDocs;
import com.ticket.core.config.AdmissionTokenValidator;
import com.ticket.core.config.security.MemberPrincipal;
import com.ticket.core.domain.performanceseat.command.DeselectAllSeatsUseCase;
import com.ticket.core.domain.performanceseat.command.DeselectSeatUseCase;
import com.ticket.core.domain.performanceseat.command.SelectSeatUseCase;
import com.ticket.core.support.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
            final MemberPrincipal memberPrincipal,
            final HttpServletRequest servletRequest
    ) {
        admissionTokenValidator.verify(servletRequest, memberPrincipal.getMemberId(), performanceId);
        selectSeatUseCase.execute(new SelectSeatUseCase.Input(performanceId, seatId, memberPrincipal.getMemberId()));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/{seatId}/select")
    public ApiResponse<Void> deselectSeat(
            @PathVariable final Long performanceId,
            @PathVariable final Long seatId,
            final MemberPrincipal memberPrincipal,
            final HttpServletRequest servletRequest
    ) {
        admissionTokenValidator.verify(servletRequest, memberPrincipal.getMemberId(), performanceId);
        deselectSeatUseCase.execute(new DeselectSeatUseCase.Input(performanceId, seatId, memberPrincipal.getMemberId()));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/select")
    public ApiResponse<Void> deselectAllSeats(
            @PathVariable final Long performanceId,
            final MemberPrincipal memberPrincipal,
            final HttpServletRequest servletRequest
    ) {
        admissionTokenValidator.verify(servletRequest, memberPrincipal.getMemberId(), performanceId);
        deselectAllSeatsUseCase.execute(new DeselectAllSeatsUseCase.Input(performanceId, memberPrincipal.getMemberId()));
        return ApiResponse.success();
    }
}
