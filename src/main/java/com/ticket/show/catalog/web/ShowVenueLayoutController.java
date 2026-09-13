package com.ticket.show.catalog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.shared.web.ApiResponse;
import com.ticket.show.catalog.application.usecase.GetShowVenueLayoutUseCase;
import com.ticket.show.catalog.web.docs.ShowVenueLayoutControllerDocs;

import lombok.RequiredArgsConstructor;

/**
 * {@code /api/v1/shows/{showId}/venue-layout}은 물리적 좌석 배치(Venue layout)만 다룬다.
 *
 * <p>원래 booking 소유였다 — booking 데이터를 전혀 참조하지 않는 passthrough였고(venue 표시값만 조합), Venue BC 재편으로 show가
 * venue module의 공개 계약을 직접 호출하도록 옮겨왔다. URL·응답 계약은 그대로다.
 *
 * <p>{@code /api/v1/shows/{showId}/seats}(show 기준 좌석·등급 조회)는 ADR 0005(ShowGrade/ShowSeat 폐기)로 제거했다.
 * 등급·가격은 이제 회차(Performance) 단위로만 존재하므로 show 기준 등급 조회 자체가 표현할 수 없는 개념이다. 회차 기준 seat-map API는
 * booking의 {@code PerformanceSeatQueryController}가 제공한다.
 */
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowVenueLayoutController implements ShowVenueLayoutControllerDocs {
    private final GetShowVenueLayoutUseCase getShowVenueLayoutUseCase;

    @Override
    @GetMapping("/{showId}/venue-layout")
    public ApiResponse<GetShowVenueLayoutUseCase.Output> getVenueLayout(
            @PathVariable final Long showId) {
        final GetShowVenueLayoutUseCase.Input input = new GetShowVenueLayoutUseCase.Input(showId);
        return ApiResponse.success(getShowVenueLayoutUseCase.execute(input));
    }
}
