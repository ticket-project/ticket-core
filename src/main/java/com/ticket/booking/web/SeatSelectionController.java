package com.ticket.booking.web;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.application.usecase.DeselectAllSeatsUseCase;
import com.ticket.booking.application.usecase.DeselectSeatUseCase;
import com.ticket.booking.application.usecase.SelectSeatUseCase;
import com.ticket.booking.web.docs.SeatSelectionControllerDocs;
import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

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
            // 헤더 이름은 ticket-queue와 맞춘 계약이다. admission 내부 상수를 import하지 않는다.
            @RequestHeader(value = "X-Admission-Token", required = false)
                    final String admissionToken,
            final AuthenticatedMember member) {
        selectSeatUseCase.execute(
                new SelectSeatUseCase.Input(
                        performanceId, seatId, member.memberId(), admissionToken));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/{seatId}/select")
    public ApiResponse<Void> deselectSeat(
            @PathVariable final Long performanceId,
            @PathVariable final Long seatId,
            final AuthenticatedMember member) {
        deselectSeatUseCase.execute(
                new DeselectSeatUseCase.Input(performanceId, seatId, member.memberId()));
        return ApiResponse.success();
    }

    @Override
    @DeleteMapping("/select")
    public ApiResponse<Void> deselectAllSeats(
            @PathVariable final Long performanceId, final AuthenticatedMember member) {
        deselectAllSeatsUseCase.execute(
                new DeselectAllSeatsUseCase.Input(performanceId, member.memberId()));
        return ApiResponse.success();
    }
}
