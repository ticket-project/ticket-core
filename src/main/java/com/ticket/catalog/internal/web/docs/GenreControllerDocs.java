package com.ticket.catalog.internal.web.docs;

import com.ticket.catalog.internal.application.show.query.GetGenresByCategoryUseCase;
import com.ticket.core.support.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "장르(Genre)", description = "장르 관련 API")
public interface GenreControllerDocs {

    @Operation(
            summary = "장르 목록 조회",
            description = "카테고리별 장르 목록을 조회합니다. 카테고리 코드를 지정하지 않으면 전체 장르를 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ApiResponse<List<GetGenresByCategoryUseCase.GenreItem>> getGenres(
            @Parameter(description = "카테고리 코드 (예: CONCERT, THEATER, MUSICAL)", example = "CONCERT") String category
    );
}
