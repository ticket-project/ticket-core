package com.ticket.show.endpoint;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.shared.web.ApiResponse;
import com.ticket.shared.web.SliceResponse;
import com.ticket.show.endpoint.cursor.ShowCursorCodec;
import com.ticket.show.endpoint.docs.ShowControllerDocs;
import com.ticket.show.endpoint.request.SaleOpeningSoonRequest;
import com.ticket.show.endpoint.request.ShowListRequest;
import com.ticket.show.endpoint.request.ShowSearchRequest;
import com.ticket.show.query.ShowSort;
import com.ticket.show.usecase.CountSearchShowsUseCase;
import com.ticket.show.usecase.GetLatestShowsUseCase;
import com.ticket.show.usecase.GetSaleOpeningSoonShowsPageUseCase;
import com.ticket.show.usecase.GetSaleOpeningSoonShowsUseCase;
import com.ticket.show.usecase.GetShowDetailUseCase;
import com.ticket.show.usecase.GetShowsUseCase;
import com.ticket.show.usecase.SearchShowsUseCase;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowController implements ShowControllerDocs {
    private final GetShowsUseCase getShowsUseCase;
    private final GetLatestShowsUseCase getLatestShowsUseCase;
    private final GetSaleOpeningSoonShowsUseCase getSaleOpeningSoonShowsUseCase;
    private final GetSaleOpeningSoonShowsPageUseCase getSaleOpeningSoonShowsPageUseCase;
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
    public ApiResponse<SliceResponse<GetShowsUseCase.Item>> getShowsPage(
            @ParameterObject final ShowListRequest request,
            @RequestParam(defaultValue = "5") final int size,
            @RequestParam(defaultValue = "popular") final String sort) {
        final GetShowsUseCase.Input input =
                new GetShowsUseCase.Input(
                        request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetShowsUseCase.Output output = getShowsUseCase.execute(input);
        return ApiResponse.success(
                SliceResponse.of(
                        output.items(),
                        output.hasNext(),
                        size,
                        showCursorCodec.encode(output.nextPosition())));
    }

    @Override
    @GetMapping("/latest")
    public ApiResponse<GetLatestShowsUseCase.Output> getLatestShows(
            @RequestParam(defaultValue = "CONCERT") final String category) {
        GetLatestShowsUseCase.Input input = new GetLatestShowsUseCase.Input(category);
        return ApiResponse.success(getLatestShowsUseCase.execute(input));
    }

    @Override
    @GetMapping("/sale-opening-soon")
    public ApiResponse<GetSaleOpeningSoonShowsUseCase.Output> getShowsSaleOpeningSoon(
            @RequestParam(defaultValue = "CONCERT") final String category,
            @RequestParam(defaultValue = "5") final int size) {
        GetSaleOpeningSoonShowsUseCase.Input input =
                new GetSaleOpeningSoonShowsUseCase.Input(category, size);
        return ApiResponse.success(getSaleOpeningSoonShowsUseCase.execute(input));
    }

    @Override
    @GetMapping("/sale-opening-soon/page")
    public ApiResponse<SliceResponse<GetSaleOpeningSoonShowsPageUseCase.Item>>
            getShowsSaleOpeningSoonPage(
                    @ParameterObject final SaleOpeningSoonRequest request,
                    @RequestParam(defaultValue = "16") final int size,
                    @RequestParam(defaultValue = "saleStartApproaching") final String sort) {
        final GetSaleOpeningSoonShowsPageUseCase.Input input =
                new GetSaleOpeningSoonShowsPageUseCase.Input(
                        request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetSaleOpeningSoonShowsPageUseCase.Output output =
                getSaleOpeningSoonShowsPageUseCase.execute(input);
        return ApiResponse.success(
                SliceResponse.of(
                        output.items(),
                        output.hasNext(),
                        size,
                        showCursorCodec.encode(output.nextPosition())));
    }

    @Override
    @GetMapping("/search")
    public ApiResponse<SliceResponse<SearchShowsUseCase.Item>> searchShows(
            @ParameterObject final ShowSearchRequest request,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(defaultValue = "popular") final String sort) {
        final SearchShowsUseCase.Input input =
                new SearchShowsUseCase.Input(
                        request.toCriteria(showCursorCodec), size, ShowSort.from(sort));
        final SearchShowsUseCase.Output output = searchShowsUseCase.execute(input);
        return ApiResponse.success(
                SliceResponse.of(
                        output.items(),
                        output.hasNext(),
                        size,
                        showCursorCodec.encode(output.nextPosition())));
    }

    @Override
    @GetMapping("/search/count")
    public ApiResponse<CountSearchShowsUseCase.Output> countSearchShows(
            @ParameterObject final ShowSearchRequest request) {
        final CountSearchShowsUseCase.Input input =
                new CountSearchShowsUseCase.Input(request.toCountCriteria());
        return ApiResponse.success(countSearchShowsUseCase.execute(input));
    }
}
