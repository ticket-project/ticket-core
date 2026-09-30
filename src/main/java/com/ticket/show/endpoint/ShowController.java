package com.ticket.show.endpoint;

import jakarta.validation.constraints.Positive;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "공연(Show)", description = "공연 정보·공연장 레이아웃 조회 API")
public class ShowController {
    private final GetShowsUseCase getShowsUseCase;
    private final GetLatestShowsUseCase getLatestShowsUseCase;
    private final GetSaleOpeningSoonShowsUseCase getSaleOpeningSoonShowsUseCase;
    private final GetSaleOpeningSoonShowsPageUseCase getSaleOpeningSoonShowsPageUseCase;
    private final SearchShowsUseCase searchShowsUseCase;
    private final CountSearchShowsUseCase countSearchShowsUseCase;
    private final GetShowDetailUseCase getShowDetailUseCase;
    private final GetMyShowLikesUseCase getMyShowLikesUseCase;
    private final ShowCursorCodec showCursorCodec;

    @Operation(summary = "공연 상세 조회", description = """
            공연 ID로 상세 정보를 조회합니다.
            출연자, 장르, 좌석 등급/가격, 공연 회차 등 모든 정보를 포함합니다.
            """)
    @GetMapping("/api/v1/shows/{id}")
    public ApiResponse<GetShowDetailUseCase.Output> getShowDetail(
            @Parameter(description = "공연 ID", example = "1") @PathVariable @Positive final Long id) {
        final GetShowDetailUseCase.Input input = new GetShowDetailUseCase.Input(id);
        return ApiResponse.success(getShowDetailUseCase.execute(input));
    }

    @Operation(summary = "공연 조회 (무한스크롤)", description = """
            공연 목록을 커서 기반 무한스크롤 방식으로 조회합니다.

            ## 사용 방법
            1. **첫 요청**: `cursor` 파라미터 없이 호출
            2. **다음 페이지**: 응답의 `nextCursor` 값을 `cursor` 파라미터로 전달
            3. **종료 조건**: `hasNext`가 `false`이면 더 이상 데이터 없음

            ## 정렬 옵션 (sort 파라미터)
            - `popular` (기본값) - 인기순 (조회수 높은 순)
            - `latest` - 최신순 (생성일 최신순)
            - `showStartApproaching` - 공연 임박순 (공연 시작일 가까운 순)
            """)
    @GetMapping("/api/v1/shows")
    public ApiResponse<SliceResponse<GetShowsUseCase.ShowResponse>> getShowsPage(
            @ParameterObject final ShowListRequest request,
            @Parameter(description = "한 번에 조회할 개수 (기본값: 5, 최대: 100)") @RequestParam(defaultValue = "5") @Positive
                    final int size,
            @Parameter(description = "정렬 기준 [popular(인기순), latest(최신순), showStartApproaching(공연임박순)]")
                    @RequestParam(defaultValue = "popular")
                    final String sort) {
        final GetShowsUseCase.Input input =
                new GetShowsUseCase.Input(request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetShowsUseCase.Output output = getShowsUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(), output.hasNext(), size, showCursorCodec.encode(output.nextPosition())));
    }

    @Operation(summary = "메인 홈 최신 공연 목록 조회", description = "특정 카테고리의 최신 등록된 공연 10개를 조회합니다.")
    @GetMapping("/api/v1/shows/latest")
    public ApiResponse<GetLatestShowsUseCase.Output> getLatestShows(
            @Parameter(description = "카테고리", example = "CONCERT") @RequestParam(defaultValue = "CONCERT")
                    final String category) {
        GetLatestShowsUseCase.Input input = new GetLatestShowsUseCase.Input(category);
        return ApiResponse.success(getLatestShowsUseCase.execute(input));
    }

    @Operation(summary = "메인 홈 오픈예정 공연 목록 조회", description = "특정 카테고리의 예매오픈마감 임박 순 공연 5개를 조회합니다.")
    @GetMapping("/api/v1/shows/sale-opening-soon")
    public ApiResponse<GetSaleOpeningSoonShowsUseCase.Output> getShowsSaleOpeningSoon(
            @Parameter(description = "카테고리", example = "CONCERT") @RequestParam(defaultValue = "CONCERT")
                    final String category,
            @Parameter(description = "조회 개수") @RequestParam(defaultValue = "5") @Positive final int size) {
        GetSaleOpeningSoonShowsUseCase.Input input = new GetSaleOpeningSoonShowsUseCase.Input(category, size);
        return ApiResponse.success(getSaleOpeningSoonShowsUseCase.execute(input));
    }

    @Operation(summary = "판매 오픈 예정 공연 목록 조회 (무한스크롤)", description = """
            판매 오픈 예정 공연 목록을 커서 기반 무한스크롤로 조회합니다.

            ## 검색 조건
            - **title**: 공연 제목 (부분 일치 검색)
            - **saleStartDateFrom/To**: 판매 시작일 범위
            - **saleEndDateFrom/To**: 판매 종료일 범위
            - **category**: 카테고리 필터

            ## 정렬 옵션
            - `saleStartApproaching` (기본값) - 판매 시작일 오름차순
            - `popular` - 인기순 (조회수 높은 순)
            """)
    @GetMapping("/api/v1/shows/sale-opening-soon/page")
    public ApiResponse<SliceResponse<GetSaleOpeningSoonShowsPageUseCase.ShowResponse>> getShowsSaleOpeningSoonPage(
            @ParameterObject final SaleOpeningSoonRequest request,
            @Parameter(description = "한 번에 조회할 개수 (기본값: 16)") @RequestParam(defaultValue = "16") @Positive
                    final int size,
            @Parameter(description = "정렬 기준 [saleStartApproaching(판매시작일순), popular(인기순), latest(최신순)]")
                    @RequestParam(defaultValue = "saleStartApproaching")
                    final String sort) {
        final GetSaleOpeningSoonShowsPageUseCase.Input input = new GetSaleOpeningSoonShowsPageUseCase.Input(
                request.toParam(showCursorCodec), size, ShowSort.from(sort));
        final GetSaleOpeningSoonShowsPageUseCase.Output output = getSaleOpeningSoonShowsPageUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(), output.hasNext(), size, showCursorCodec.encode(output.nextPosition())));
    }

    @Operation(summary = "공연 검색 (무한스크롤)", description = """
            공연을 다양한 조건으로 검색합니다.

            ## 검색 조건
            - **keyword**: 공연명 검색 (부분 일치)
            - **category**: 카테고리 필터 (CONCERT, THEATER, MUSICAL 등)
            - **bookingStatus**: 예매 상태 필터 (BEFORE_OPEN, ON_SALE, CLOSED)
            - **startDateFrom/To**: 공연 시작일 범위
            - **region**: 지역 필터

            ## 정렬 옵션
            - `popular` (기본값) - 조회순 (조회수 높은 순)
            - `showStartApproaching` - 공연 임박순 (공연 시작일 가까운 순)
            """)
    @GetMapping("/api/v1/shows/search")
    public ApiResponse<SliceResponse<SearchShowsUseCase.ShowResponse>> searchShows(
            @ParameterObject final ShowSearchRequest request,
            @Parameter(description = "한 번에 조회할 개수 (기본값: 20)") @RequestParam(defaultValue = "20") @Positive
                    final int size,
            @Parameter(description = "정렬 기준 [popular(조회순), showStartApproaching(공연임박순)]")
                    @RequestParam(defaultValue = "popular")
                    final String sort) {
        final SearchShowsUseCase.Input input =
                new SearchShowsUseCase.Input(request.toCriteria(showCursorCodec), size, ShowSort.from(sort));
        final SearchShowsUseCase.Output output = searchShowsUseCase.execute(input);
        return ApiResponse.success(SliceResponse.of(
                output.items(), output.hasNext(), size, showCursorCodec.encode(output.nextPosition())));
    }

    @Operation(summary = "공연 검색 결과 개수 조회", description = """
            필터 조건에 맞는 공연 개수만 조회합니다.
            필터 변경 시 실제 데이터 없이 개수만 빠르게 확인할 때 사용합니다.
            """)
    @GetMapping("/api/v1/shows/search/count")
    public ApiResponse<CountSearchShowsUseCase.Output> countSearchShows(
            @ParameterObject final ShowSearchRequest request) {
        final CountSearchShowsUseCase.Input input = new CountSearchShowsUseCase.Input(request.toCountCriteria());
        return ApiResponse.success(countSearchShowsUseCase.execute(input));
    }

    /** 찜 항목의 공연·공연장 표시값은 show가 조립한다. 기존 회원 URL을 유지한다. */
    @Operation(summary = "내 찜 목록 조회", description = "로그인한 회원의 찜 목록을 커서 기반 페이지네이션으로 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/api/v1/members/me/likes")
    public ApiResponse<SliceResponse<GetMyShowLikesUseCase.ShowLikeResponse>> getMyLikes(
            @Parameter(hidden = true) final AuthenticatedMember member,
            @Parameter(description = "커서(마지막 찜 ID)", example = "123") @RequestParam(required = false)
                    final String cursor,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") @Positive final int size) {
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
