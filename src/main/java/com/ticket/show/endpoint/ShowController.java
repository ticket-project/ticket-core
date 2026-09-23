package com.ticket.show.endpoint;

import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.shared.web.ApiResponse;
import com.ticket.shared.web.SliceResponse;
import com.ticket.show.endpoint.cursor.ShowCursorCodec;
import com.ticket.show.endpoint.docs.ShowControllerDocs;
import com.ticket.show.endpoint.request.SaleOpeningSoonRequest;
import com.ticket.show.endpoint.request.ShowListRequest;
import com.ticket.show.endpoint.request.ShowSearchRequest;
import com.ticket.show.usecase.CountSearchShowsUseCase;
import com.ticket.show.usecase.GetLatestShowsUseCase;
import com.ticket.show.usecase.GetMyShowLikesUseCase;
import com.ticket.show.usecase.GetSaleOpeningSoonShowsPageUseCase;
import com.ticket.show.usecase.GetSaleOpeningSoonShowsUseCase;
import com.ticket.show.usecase.GetShowDetailUseCase;
import com.ticket.show.usecase.GetShowsUseCase;
import com.ticket.show.usecase.SearchShowsUseCase;
import com.ticket.show.usecase.ShowSort;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ShowController implements ShowControllerDocs {
    private final GetShowsUseCase getShowsUseCase;
    private final GetLatestShowsUseCase getLatestShowsUseCase;
    private final GetSaleOpeningSoonShowsUseCase getSaleOpeningSoonShowsUseCase;
    private final GetSaleOpeningSoonShowsPageUseCase getSaleOpeningSoonShowsPageUseCase;
    private final SearchShowsUseCase searchShowsUseCase;
    private final CountSearchShowsUseCase countSearchShowsUseCase;
    private final GetShowDetailUseCase getShowDetailUseCase;
    private final GetMyShowLikesUseCase getMyShowLikesUseCase;
    private final ShowCursorCodec showCursorCodec;

    @Override
    @GetMapping("/api/v1/shows/{id}")
    public ApiResponse<GetShowDetailUseCase.Output> getShowDetail(@PathVariable final Long id) {
        final GetShowDetailUseCase.Input input = new GetShowDetailUseCase.Input(id);
        return ApiResponse.success(getShowDetailUseCase.execute(input));
    }

    @Override
    @GetMapping("/api/v1/shows")
    public ApiResponse<SliceResponse<GetShowsUseCase.Item>> getShowsPage(
            @ParameterObject final ShowListRequest request,
            @RequestParam(defaultValue = "5") final int size,
            @RequestParam(defaultValue = "popular") final String sort) {
        final GetShowsUseCase.Input input =
                new GetShowsUseCase.Input(request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetShowsUseCase.Output output = getShowsUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(), output.hasNext(), size, showCursorCodec.encode(output.nextPosition())));
    }

    @Override
    @GetMapping("/api/v1/shows/latest")
    public ApiResponse<GetLatestShowsUseCase.Output> getLatestShows(
            @RequestParam(defaultValue = "CONCERT") final String category) {
        GetLatestShowsUseCase.Input input = new GetLatestShowsUseCase.Input(category);
        return ApiResponse.success(getLatestShowsUseCase.execute(input));
    }

    @Override
    @GetMapping("/api/v1/shows/sale-opening-soon")
    public ApiResponse<GetSaleOpeningSoonShowsUseCase.Output> getShowsSaleOpeningSoon(
            @RequestParam(defaultValue = "CONCERT") final String category,
            @RequestParam(defaultValue = "5") final int size) {
        GetSaleOpeningSoonShowsUseCase.Input input = new GetSaleOpeningSoonShowsUseCase.Input(category, size);
        return ApiResponse.success(getSaleOpeningSoonShowsUseCase.execute(input));
    }

    @Override
    @GetMapping("/api/v1/shows/sale-opening-soon/page")
    public ApiResponse<SliceResponse<GetSaleOpeningSoonShowsPageUseCase.Item>> getShowsSaleOpeningSoonPage(
            @ParameterObject final SaleOpeningSoonRequest request,
            @RequestParam(defaultValue = "16") final int size,
            @RequestParam(defaultValue = "saleStartApproaching") final String sort) {
        final GetSaleOpeningSoonShowsPageUseCase.Input input = new GetSaleOpeningSoonShowsPageUseCase.Input(
                request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetSaleOpeningSoonShowsPageUseCase.Output output = getSaleOpeningSoonShowsPageUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(), output.hasNext(), size, showCursorCodec.encode(output.nextPosition())));
    }

    @Override
    @GetMapping("/api/v1/shows/search")
    public ApiResponse<SliceResponse<SearchShowsUseCase.Item>> searchShows(
            @ParameterObject final ShowSearchRequest request,
            @RequestParam(defaultValue = "20") final int size,
            @RequestParam(defaultValue = "popular") final String sort) {
        final SearchShowsUseCase.Input input =
                new SearchShowsUseCase.Input(request.toCriteria(showCursorCodec), size, ShowSort.from(sort));
        final SearchShowsUseCase.Output output = searchShowsUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(), output.hasNext(), size, showCursorCodec.encode(output.nextPosition())));
    }

    @Override
    @GetMapping("/api/v1/shows/search/count")
    public ApiResponse<CountSearchShowsUseCase.Output> countSearchShows(
            @ParameterObject final ShowSearchRequest request) {
        final CountSearchShowsUseCase.Input input = new CountSearchShowsUseCase.Input(request.toCountCriteria());
        return ApiResponse.success(countSearchShowsUseCase.execute(input));
    }

    /** 찜 항목의 공연·공연장 표시값은 show가 조립한다. 기존 회원 URL을 유지한다. */
    @Override
    @GetMapping("/api/v1/members/me/likes")
    public ApiResponse<SliceResponse<GetMyShowLikesUseCase.Item>> getMyLikes(
            final AuthenticatedMember member,
            @RequestParam(required = false) final String cursor,
            @RequestParam(defaultValue = "20") final int size) {
        final GetMyShowLikesUseCase.Input input =
                new GetMyShowLikesUseCase.Input(member.memberId(), decodeLikeCursor(cursor), size);
        final GetMyShowLikesUseCase.Output output = getMyShowLikesUseCase.execute(input);
        return ApiResponse.success(
                SliceResponse.of(output.items(), output.hasNext(), size, encodeLikeCursor(output.nextPosition())));
    }

    /** 찜 목록 커서는 마지막 찜 id의 십진수 문자열이다. 공연 목록의 복합 커서와 구분한다. */
    private static @Nullable Long decodeLikeCursor(final @Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (final NumberFormatException exception) {
            throw new InvalidRequestException("cursor 형식이 올바르지 않습니다.");
        }
    }

    private static @Nullable String encodeLikeCursor(final @Nullable Long lastLikeId) {
        return lastLikeId == null ? null : String.valueOf(lastLikeId);
    }
}
