package com.ticket.booking.internal.web;

import com.ticket.booking.internal.web.docs.ShowSeatViewControllerDocs;
import com.ticket.booking.internal.application.performanceseat.query.GetShowSeatsUseCase;
import com.ticket.booking.internal.application.performanceseat.query.GetVenueLayoutUseCase;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/shows/{showId}/venue-layout}, {@code /api/v1/shows/{showId}/seats}는 물리적 좌석 배치가 아니라
 * 회차별 판매 상태(PerformanceSeat)를 함께 다루던 기존 {@code performanceseat} 유스케이스를 그대로 쓴다.
 *
 * <p>{@code PerformanceSeat}는 이번 catalog 이동(Task 5) 범위 밖이고 booking 소유로 Task 7에서 옮겨간다.
 * 그래서 순수 catalog 엔드포인트(공연 상세/목록/검색 등)만 {@code com.ticket.catalog.internal.web.ShowController}로
 * 옮기고, 이 두 엔드포인트는 legacy {@code com.ticket.core.api.controller}에 남겨 catalog가 아직 legacy인
 * performanceseat 유스케이스를 참조하지 않게 한다. URL·JSON 계약은 기존과 동일하다.
 */
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowSeatViewController implements ShowSeatViewControllerDocs {

    private final GetShowSeatsUseCase getShowSeatsUseCase;
    private final GetVenueLayoutUseCase getVenueLayoutUseCase;

    @Override
    @GetMapping("/{showId}/venue-layout")
    public ApiResponse<GetVenueLayoutUseCase.Output> getVenueLayout(@PathVariable final Long showId) {
        final GetVenueLayoutUseCase.Input input = new GetVenueLayoutUseCase.Input(showId);
        return ApiResponse.success(getVenueLayoutUseCase.execute(input));
    }

    @Override
    @GetMapping("/{showId}/seats")
    public ApiResponse<GetShowSeatsUseCase.Output> getShowSeats(@PathVariable final Long showId) {
        final GetShowSeatsUseCase.Input input = new GetShowSeatsUseCase.Input(showId);
        return ApiResponse.success(getShowSeatsUseCase.execute(input));
    }
}
