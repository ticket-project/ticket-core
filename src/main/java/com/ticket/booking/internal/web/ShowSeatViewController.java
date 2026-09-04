package com.ticket.booking.internal.web;

import com.ticket.booking.internal.web.docs.ShowSeatViewControllerDocs;
import com.ticket.booking.internal.application.performanceseat.query.GetVenueLayoutUseCase;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/shows/{showId}/venue-layout}은 물리적 좌석 배치가 아니라 회차별 판매 상태
 * (PerformanceSeat)를 함께 다루던 기존 {@code performanceseat} 유스케이스를 그대로 쓴다.
 *
 * <p>{@code /api/v1/shows/{showId}/seats}(show 기준 좌석·등급 조회)는 ADR 0005(ShowGrade/ShowSeat
 * 폐기)로 제거했다. 등급·가격은 이제 회차(Performance) 단위로만 존재하므로 show 기준 등급 조회
 * 자체가 표현할 수 없는 개념이다. 대체 API(회차 기준 seat-map)는 Phase 4에서 추가한다.
 */
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowSeatViewController implements ShowSeatViewControllerDocs {

    private final GetVenueLayoutUseCase getVenueLayoutUseCase;

    @Override
    @GetMapping("/{showId}/venue-layout")
    public ApiResponse<GetVenueLayoutUseCase.Output> getVenueLayout(@PathVariable final Long showId) {
        final GetVenueLayoutUseCase.Input input = new GetVenueLayoutUseCase.Input(showId);
        return ApiResponse.success(getVenueLayoutUseCase.execute(input));
    }
}
