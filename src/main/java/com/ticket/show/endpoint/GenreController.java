package com.ticket.show.endpoint;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.shared.web.ApiResponse;
import com.ticket.show.usecase.GetGenresByCategoryUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** 장르 관련 API Controller */
@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
@Tag(name = "장르(Genre)", description = "장르 관련 API")
public class GenreController {
    private final GetGenresByCategoryUseCase getGenresByCategoryUseCase;

    /** 카테고리별 장르 목록 조회 - categoryCode가 없으면 전체 장르 조회 - categoryCode가 있으면 해당 카테고리에 속한 장르만 조회 */
    @Operation(summary = "장르 목록 조회", description = "카테고리별 장르 목록을 조회합니다. 카테고리 코드를 지정하지 않으면 전체 장르를 조회합니다.")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")})
    @GetMapping
    public ApiResponse<List<GetGenresByCategoryUseCase.GenreResponse>> getGenres(
            @Parameter(description = "카테고리 코드 (예: CONCERT, THEATER, MUSICAL)", example = "CONCERT")
                    @RequestParam(required = false)
                    final String category) {
        final GetGenresByCategoryUseCase.Input input = new GetGenresByCategoryUseCase.Input(category);
        final GetGenresByCategoryUseCase.Output output = getGenresByCategoryUseCase.execute(input);
        return ApiResponse.success(output.genres());
    }
}
