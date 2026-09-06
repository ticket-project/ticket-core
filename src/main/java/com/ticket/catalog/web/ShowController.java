package com.ticket.catalog.web;

import com.ticket.catalog.web.request.SaleOpeningSoonRequest;
import com.ticket.catalog.web.request.ShowListRequest;
import com.ticket.catalog.web.request.ShowSearchRequest;
import com.ticket.catalog.web.support.cursor.ShowCursorCodec;
import com.ticket.catalog.web.docs.ShowControllerDocs;
import com.ticket.catalog.application.show.query.model.ShowListItemView;
import com.ticket.catalog.application.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.catalog.application.show.query.model.ShowSearchItemView;
import com.ticket.catalog.application.show.query.CountSearchShowsUseCase;
import com.ticket.catalog.application.show.query.GetGenresByCategoryUseCase;
import com.ticket.catalog.application.show.query.GetLatestShowsUseCase;
import com.ticket.catalog.application.show.query.GetSaleStartApproachingShowsPageUseCase;
import com.ticket.catalog.application.show.query.GetSaleStartApproachingShowsUseCase;
import com.ticket.catalog.application.show.query.GetShowDetailUseCase;
import com.ticket.catalog.application.show.query.GetShowsUseCase;
import com.ticket.catalog.application.show.query.SearchShowsUseCase;
import com.ticket.catalog.application.show.query.ShowSort;
import com.ticket.web.ApiResponse;
import com.ticket.web.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowController implements ShowControllerDocs {

    private final GetShowsUseCase getShowsUseCase;
    private final GetLatestShowsUseCase getLatestShowsUseCase;
    private final GetSaleStartApproachingShowsUseCase getSaleStartApproachingShowsUseCase;
    private final GetSaleStartApproachingShowsPageUseCase getSaleStartApproachingShowsPageUseCase;
    private final SearchShowsUseCase searchShowsUseCase;
    private final CountSearchShowsUseCase countSearchShowsUseCase;
    private final GetShowDetailUseCase getShowDetailUseCase;
    private final ShowCursorCodec showCursorCodec;

    @Override
    @GetMapping("/{id}")
    public ApiResponse<GetShowDetailUseCase.Output> getShowDetail(@PathVariable final Long id) {
        final GetShowDetailUseCase.Input input = new GetShowDetailUseCase.Input(id);
        return ApiResponse.success(getShowDetailUseCase.execute(input));
    }

    @Override
    @GetMapping
    public ApiResponse<SliceResponse<ShowListItemView>> getShowsPage(
            @ParameterObject final ShowListRequest request,
            @RequestParam(defaultValue = "5") final int size,
            @RequestParam(defaultValue = "popular") final String sort
    ) {
        final GetShowsUseCase.Input input =
                new GetShowsUseCase.Input(request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetShowsUseCase.Output output = getShowsUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(),
                output.hasNext(),
                size,
                showCursorCodec.encode(output.nextPosition())
        ));
    }

    @Override
    @GetMapping("/latest")
    public ApiResponse<GetLatestShowsUseCase.Output> getLatestShows(
            @RequestParam(defaultValue = "CONCERT") final String category
    ) {
        GetLatestShowsUseCase.Input input = new GetLatestShowsUseCase.Input(category);
        return ApiResponse.success(getLatestShowsUseCase.execute(input));
    }

    @Override
    @GetMapping("/sale-opening-soon")
    public ApiResponse<GetSaleStartApproachingShowsUseCase.Output> getShowsSaleOpeningSoon(
            @RequestParam(defaultValue = "CONCERT") final String category,
            @RequestParam(defaultValue = "5") final int size
    ) {
        GetSaleStartApproachingShowsUseCase.Input input = new GetSaleStartApproachingShowsUseCase.Input(category, size);
        return ApiResponse.success(getSaleStartApproachingShowsUseCase.execute(input));
    }

    @Override
    @GetMapping("/sale-opening-soon/page")
    public ApiResponse<SliceResponse<ShowOpeningSoonDetailView>> getShowsSaleOpeningSoonPage(
            @ParameterObject final SaleOpeningSoonRequest request,
            @RequestParam(defaultValue = "16") final int size,
            @RequestParam(defaultValue = "saleStartApproaching") final String sort
    ) {
        final GetSaleStartApproachingShowsPageUseCase.Input input =
                new GetSaleStartApproachingShowsPageUseCase.Input(request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetSaleStartApproachingShowsPageUseCase.Output output = getSaleStartApproachingShowsPageUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(),
                output.hasNext(),
                size,
                showCursorCodec.encode(output.nextPosition())
        ));
    }

    @Override
    @GetMapping("/search")
    public ApiResponse<SliceResponse<ShowSearchItemView>> searchShows(
            @ParameterObject final ShowSearchRequest request,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(defaultValue = "popular") final String sort
    ) {
        final SearchShowsUseCase.Input input =
                new SearchShowsUseCase.Input(request.toCriteria(showCursorCodec), size, ShowSort.from(sort));
        final SearchShowsUseCase.Output output = searchShowsUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(),
                output.hasNext(),
                size,
                showCursorCodec.encode(output.nextPosition())
        ));
    }

    @Override
    @GetMapping("/search/count")
    public ApiResponse<CountSearchShowsUseCase.Output> countSearchShows(
            @ParameterObject final ShowSearchRequest request
    ) {
        final CountSearchShowsUseCase.Input input = new CountSearchShowsUseCase.Input(request.toCountCriteria());
        return ApiResponse.success(countSearchShowsUseCase.execute(input));
    }
}
