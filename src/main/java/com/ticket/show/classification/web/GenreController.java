package com.ticket.show.classification.web;

import com.ticket.show.classification.web.docs.GenreControllerDocs;
import com.ticket.show.classification.application.usecase.GetGenresByCategoryUseCase;
import com.ticket.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 장르 관련 API Controller
 */
@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
public class GenreController implements GenreControllerDocs {

    private final GetGenresByCategoryUseCase getGenresByCategoryUseCase;

    /**
     * 카테고리별 장르 목록 조회
     * - categoryCode가 없으면 전체 장르 조회
     * - categoryCode가 있으면 해당 카테고리에 속한 장르만 조회
     */
    @Override
    @GetMapping
    public ApiResponse<List<GetGenresByCategoryUseCase.GenreItem>> getGenres(
            @RequestParam(required = false) final String category
    ) {
        final GetGenresByCategoryUseCase.Input input = new GetGenresByCategoryUseCase.Input(category);
        final GetGenresByCategoryUseCase.Output output = getGenresByCategoryUseCase.execute(input);
        return ApiResponse.success(output.genres());
    }
}
