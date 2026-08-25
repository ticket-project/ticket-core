package com.ticket.core.api.controller.docs;

import com.ticket.core.api.controller.request.ShowSearchRequest;
import com.ticket.core.app.performanceseat.query.GetShowSeatsUseCase;
import com.ticket.core.app.performanceseat.query.GetVenueLayoutUseCase;
import com.ticket.core.domain.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.core.domain.show.query.model.ShowListItemView;
import com.ticket.core.domain.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.core.domain.show.query.model.ShowParam;
import com.ticket.core.domain.show.query.model.ShowSearchItemView;
import com.ticket.core.domain.show.query.CountSearchShowsUseCase;
import com.ticket.core.domain.show.query.GetLatestShowsUseCase;
import com.ticket.core.domain.show.query.GetSaleStartApproachingShowsPageUseCase;
import com.ticket.core.domain.show.query.GetSaleStartApproachingShowsUseCase;
import com.ticket.core.domain.show.query.GetShowDetailUseCase;
import com.ticket.core.domain.show.query.GetShowsUseCase;
import com.ticket.core.domain.show.query.SearchShowsUseCase;
import com.ticket.core.support.response.ApiResponse;
import com.ticket.core.support.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

/**
 * ShowController Swagger 문서 인터페이스
 * - Swagger 어노테이션을 Controller에서 분리하여 가독성 향상
 */
@Tag(name = "공연(Show)", description = "공연 정보 조회 API")
public interface ShowControllerDocs {

    // ========== 공연장 레이아웃 API ==========

    @Operation(
            summary = "공연장 레이아웃 조회",
            description = """
                    공연 ID로 해당 공연장의 레이아웃(SVG viewBox, 무대 위치)을 조회합니다.
                    공연장 정보는 거의 변하지 않으므로 캐싱에 적합합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ApiResponse<GetVenueLayoutUseCase.Output> getVenueLayout(
            @Parameter(description = "공연 ID", example = "1", required = true) Long showId
    );

    // ========== 좌석 정보 API ==========

    @Operation(
            summary = "공연 좌석 정보 조회",
            description = """
                    공연 ID로 해당 공연의 등급 목록과 좌석별 좌표/등급 정보를 조회합니다.
                    같은 공연의 모든 회차는 동일한 좌석 구성이므로 공연 단위로 조회합니다.
                    이 정보는 거의 변하지 않으므로 캐싱에 적합합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ApiResponse<GetShowSeatsUseCase.Output> getShowSeats(
            @Parameter(description = "공연 ID", example = "1", required = true) Long showId
    );

    // ========== 상세 조회 API ==========

    @Operation(
            summary = "공연 상세 조회",
            description = """
                    공연 ID로 상세 정보를 조회합니다.
                    출연자, 장르, 좌석 등급/가격, 공연 회차 등 모든 정보를 포함합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
            )
    })
    ApiResponse<GetShowDetailUseCase.Output> getShowDetail(
            @Parameter(description = "공연 ID", example = "1", required = true) Long id
    );

    // ========== 메인 페이지 API ==========

    @Operation(
            summary = "공연 조회 (무한스크롤)",
            description = """
                    공연 목록을 커서 기반 무한스크롤 방식으로 조회합니다.
                    
                    ## 사용 방법
                    1. **첫 요청**: `cursor` 파라미터 없이 호출
                    2. **다음 페이지**: 응답의 `nextCursor` 값을 `cursor` 파라미터로 전달
                    3. **종료 조건**: `hasNext`가 `false`이면 더 이상 데이터 없음
                    
                    ## 정렬 옵션 (sort 파라미터)
                    - `popular` (기본값) - 인기순 (조회수 높은 순)
                    - `latest` - 최신순 (생성일 최신순)
                    - `showStartApproaching` - 공연 임박순 (공연 시작일 가까운 순)
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공 응답 예시",
                                    value = """
                                            {
                                              "result": "SUCCESS",
                                              "data": {
                                                "items": [
                                                  {
                                                    "id": 20,
                                                    "title": "뮤지컬 위키드",
                                                    "subTitle": "10주년 기념 공연",
                                                    "categoryName": "뮤지컬",
                                                    "startDate": "2026-03-01",
                                                    "endDate": "2026-05-31",
                                                    "viewCount": 15000,
                                                    "saleType": {
                                                      "code": "GENERAL",
                                                      "name": "일반판매"
                                                    },
                                                    "saleStartDate": "2026-01-01",
                                                    "saleEndDate": "2026-02-28",
                                                    "createdAt": "2026-01-01T10:00:00",
                                                    "region": "SEOUL",
                                                    "venue": "블루스퀘어 신한카드홀"
                                                  }
                                                ],
                                                "hasNext": true,
                                                "size": 5,
                                                "numberOfElements": 2,
                                                "nextCursor": "eyJpZCI6MTksInN0YXJ0RGF0ZSI6IjIwMjYtMDItMjgifQ=="
                                              },
                                              "error": null
                                            }
                                            """
                            )
                    )
            )
    })
    ApiResponse<SliceResponse<ShowListItemView>> getShowsPage(
            @ParameterObject ShowParam param,
            @Parameter(description = "한 번에 조회할 개수 (기본값: 5, 최대: 100)", example = "5") int size,
            @Parameter(description = "정렬 기준 [popular(인기순), latest(최신순), showStartApproaching(공연임박순)]", example = "popular") String sort
    );

    @Operation(
            summary = "메인 홈 최신 공연 목록 조회",
            description = "특정 카테고리의 최신 등록된 공연 10개를 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공 응답 예시",
                                    value = """
                                            {
                                              "result": "SUCCESS",
                                              "data": {
                                                "shows": [
                                                  {
                                                    "id": 20,
                                                    "title": "뮤지컬 위키드",
                                                    "image": "http://example.com/image.jpg",
                                                    "startDate": "2026-03-01",
                                                    "venue": "블루스퀘어 신한카드홀",
                                                    "createdAt": "2026-01-01T10:00:00"
                                                  }
                                                ]
                                              },
                                              "error": null
                                            }
                                            """
                            )
                    )
            )
    })
    ApiResponse<GetLatestShowsUseCase.Output> getLatestShows(
            @Parameter(description = "카테고리", example = "CONCERT", required = true) String category
    );

    @Operation(
            summary = "메인 홈 오픈예정 공연 목록 조회",
            description = "특정 카테고리의 예매오픈마감 임박 순 공연 5개를 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공 응답 예시",
                                    value = """
                                            {
                                              "result": "SUCCESS",
                                              "data": {
                                                "shows": [
                                                  {
                                                    "id": 21,
                                                    "title": "뮤지컬 시카고",
                                                    "image": "http://example.com/chicago.jpg",
                                                    "venue": "디큐브 링크아트센터",
                                                    "saleStartDate": "2026-04-01"
                                                  }
                                                ]
                                              },
                                              "error": null
                                            }
                                            """
                            )
                    )
            )
    })
    ApiResponse<GetSaleStartApproachingShowsUseCase.Output> getShowsSaleOpeningSoon(
            @Parameter(description = "카테고리", example = "CONCERT", required = true) String category,
            @Parameter(description = "조회 개수", example = "5") int size
    );

    @Operation(
            summary = "판매 오픈 예정 공연 목록 조회 (무한스크롤)",
            description = """
                    판매 오픈 예정 공연 목록을 커서 기반 무한스크롤로 조회합니다.
                    
                    ## 검색 조건
                    - **title**: 공연 제목 (부분 일치 검색)
                    - **saleStartDateFrom/To**: 판매 시작일 범위
                    - **saleEndDateFrom/To**: 판매 종료일 범위
                    - **category**: 카테고리 필터
                    
                    ## 정렬 옵션
                    - `saleStartApproaching` (기본값) - 판매 시작일 오름차순
                    - `popular` - 인기순 (조회수 높은 순)
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공 응답 예시",
                                    value = """
                                            {
                                              "result": "SUCCESS",
                                              "data": {
                                                "items": [
                                                  {
                                                    "id": 21,
                                                    "title": "뮤지컬 시카고",
                                                    "image": "http://example.com/chicago.jpg",
                                                    "venue": "디큐브 링크아트센터",
                                                    "startDate": "2026-05-01",
                                                    "endDate": "2026-07-31",
                                                    "saleStartDate": "2026-04-01",
                                                    "saleEndDate": "2026-06-30"
                                                  }
                                                ],
                                                "hasNext": true,
                                                "size": 16,
                                                "numberOfElements": 16,
                                                "nextCursor": "eyJpZCI6MzYsInNhbGVTdGFydERhdGUiOiIyMDI2LTA0LTE1In0="
                                              },
                                              "error": null
                                            }
                                            """
                            )
                    )
            )
    })
    ApiResponse<SliceResponse<ShowOpeningSoonDetailView>> getShowsSaleOpeningSoonPage(
            @ParameterObject SaleOpeningSoonSearchParam param,
            @Parameter(description = "한 번에 조회할 개수 (기본값: 16)", example = "16") int size,
            @Parameter(description = "정렬 기준 [saleStartApproaching(판매시작일순), popular(인기순), latest(최신순)]", example = "saleStartApproaching") String sort
    );

    // ========== 검색 API ==========

    @Operation(
            summary = "공연 검색 (무한스크롤)",
            description = """
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
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "검색 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공 응답 예시",
                                    value = """
                                            {
                                              "result": "SUCCESS",
                                              "data": {
                                                "items": [
                                                  {
                                                    "id": 20,
                                                    "title": "뮤지컬 위키드",
                                                    "image": "http://example.com/image.jpg",
                                                    "venue": "블루스퀘어 신한카드홀",
                                                    "startDate": "2026-03-01",
                                                    "endDate": "2026-05-31",
                                                    "region": "서울",
                                                    "viewCount": 15000
                                                  }
                                                ],
                                                "hasNext": true,
                                                "size": 20,
                                                "numberOfElements": 20,
                                                "nextCursor": "eyJpZCI6MTksInZpZXdDb3VudCI6MTUwMDB9"
                                              },
                                              "error": null
                                            }
                                            """
                            )
                    )
            )
    })
    ApiResponse<SliceResponse<ShowSearchItemView>> searchShows(
            @ParameterObject ShowSearchRequest request,
            @Parameter(description = "한 번에 조회할 개수 (기본값: 20)", example = "20") int size,
            @Parameter(description = "정렬 기준 [popular(조회순), showStartApproaching(공연임박순)]", example = "popular") String sort
    );

    @Operation(
            summary = "공연 검색 결과 개수 조회",
            description = """
                    필터 조건에 맞는 공연 개수만 조회합니다.
                    필터 변경 시 실제 데이터 없이 개수만 빠르게 확인할 때 사용합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "성공 응답 예시",
                                    value = """
                                            {
                                              "result": "SUCCESS",
                                              "data": {
                                                "count": 42
                                              },
                                              "error": null
                                            }
                                            """
                            )
                    )
            )
    })
    ApiResponse<CountSearchShowsUseCase.Output> countSearchShows(
            @ParameterObject ShowSearchRequest request
    );
}
