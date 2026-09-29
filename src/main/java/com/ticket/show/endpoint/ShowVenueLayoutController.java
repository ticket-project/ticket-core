package com.ticket.show.endpoint;

import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.shared.web.ApiResponse;
import com.ticket.show.usecase.GetShowVenueLayoutUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * {@code /api/v1/shows/{showId}/venue-layout}은 물리적 좌석 배치(Venue layout)만 다룬다.
 *
 * <p>원래 booking 소유였다 — booking 데이터를 전혀 참조하지 않는 passthrough였고(venue 표시값만 조합), Venue BC 재편으로 show가 venue module의 공개 계약을
 * 직접 호출하도록 옮겨왔다. URL·응답 계약은 그대로다.
 *
 * <p>{@code /api/v1/shows/{showId}/seats}는 기존 프론트 호환을 위해 booking이 대표 회차의 좌석·가격으로 제공한다. 회차 기준 seat-map API는 booking의
 * {@code PerformanceSeatQueryController}가 제공한다.
 */
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
@Tag(name = "공연(Show)", description = "공연 정보·공연장 레이아웃 조회 API")
public class ShowVenueLayoutController {
    private final GetShowVenueLayoutUseCase getShowVenueLayoutUseCase;

    @Operation(summary = "공연장 레이아웃 조회", description = """
            공연 ID로 해당 공연장의 레이아웃(SVG viewBox, 무대 위치)을 조회합니다.
            공연장 정보는 거의 변하지 않으므로 캐싱에 적합합니다.
            """)
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")})
    @GetMapping("/{showId}/venue-layout")
    public ApiResponse<GetShowVenueLayoutUseCase.Output> getVenueLayout(
            @Parameter(description = "공연 ID", example = "1") @PathVariable @Positive final Long showId) {
        final GetShowVenueLayoutUseCase.Input input = new GetShowVenueLayoutUseCase.Input(showId);
        return ApiResponse.success(getShowVenueLayoutUseCase.execute(input));
    }
}
