package com.ticket.booking.seat.endpoint;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.booking.seat.usecase.GetShowSeatMapUseCase;
import com.ticket.shared.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/** 기존 프론트가 사용하는 공연 단위 좌석 배치도 API다. */
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowSeatMapController {
    private final GetShowSeatMapUseCase getShowSeatMapUseCase;

    @GetMapping("/{showId}/seats")
    public ApiResponse<GetShowSeatMapUseCase.Output> getSeatMap(@PathVariable final Long showId) {
        return ApiResponse.success(
                getShowSeatMapUseCase.execute(new GetShowSeatMapUseCase.Input(showId)));
    }
}
