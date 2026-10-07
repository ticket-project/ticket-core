package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requireProvided;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticket.shared.api.CursorPage;
import com.ticket.show.domain.show.SaleType;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowRepository;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowsUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final ShowRepository showRepository;
    private final VenueLookupApi venueLookupApi;

    public record Input(ShowListParam param, int size, ShowSort sort) {
        public Input {
            param = requireProvided(param, "param");
            sort = requireProvided(sort, "sort").requireOneOf(ShowSort.WITHOUT_SALE_START);
            ShowPageSize.require(size);
        }
    }

    public record Output(
            List<ShowResponse> items,
            boolean hasNext,
            @Nullable ShowCursor nextPosition) {}

    /**
     * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름
     * {@code saleType}/{@code saleStartDate}/{@code saleEndDate}는 그대로 고정한다.
     */
    public record ShowResponse(
            Long id,
            @Nullable String title,
            @Nullable String subTitle,
            @Nullable String image,
            List<String> genreNames,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            long viewCount,
            @JsonProperty("saleType") SaleType displaySaleType,
            @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt,
            @JsonProperty("saleEndDate") @Nullable LocalDateTime displaySaleEndsAt,
            LocalDateTime createdAt,
            @Nullable String region,
            @Nullable String venue) {}

    public Output execute(final Input input) {
        final CursorPage<Show, ShowCursor> page = showQuerydslRepository.findAllBySearch(
                input.param(),
                ShowListParam.venueIdsIn(venueLookupApi, input.param().region()),
                input.size(),
                input.sort());
        final Map<Long, List<String>> genreNames = showRepository.findGenreNamesByShowIds(
                page.items().stream().map(Show::getId).toList());
        final Map<Long, VenueSnapshot> venuesById = venueLookupApi.getSummaries(
                Set.copyOf(page.items().stream().map(Show::getVenueId).toList()));
        final CursorPage<ShowResponse, ShowCursor> view = page.map(show -> toResponse(show, genreNames, venuesById));
        return new Output(view.items(), view.hasNext(), view.nextPosition());
    }

    /**
     * 지역 조건을 venueId 집합으로 해석한다.
     *
     * <p><b>"지역 없음"과 "지역은 있으나 그 지역에 공연장이 없음"은 다른 결과다.</b> 그래서 {@code null}(지역 조건 자체가 없음)과 빈 집합(조건은 있는데 해당 공연장이 없으니 결과
     * 0건)을 구분해 넘긴다. 이 둘을 뭉개면 "제주에 공연장이 하나도 없다"가 "전체 목록"으로 조용히 바뀐다.
     */
    private ShowResponse toResponse(
            final Show show, final Map<Long, List<String>> genreNames, final Map<Long, VenueSnapshot> venuesById) {
        final VenueSnapshot venue = venuesById.get(show.getVenueId());
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getSubTitle(),
                ShowCardImagePathConverter.toCardImage(show.getImage()),
                genreNames.getOrDefault(show.getId(), List.of()),
                show.getStartDate(),
                show.getEndDate(),
                show.getViewCount(),
                show.getDisplaySaleType(),
                show.getDisplaySaleStartsAt(),
                show.getDisplaySaleEndsAt(),
                show.getCreatedAt(),
                venue == null ? null : venue.regionCode(),
                venue == null ? null : venue.name());
    }
}
