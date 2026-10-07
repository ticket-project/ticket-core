package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requireProvided;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    /** 판매 시작 임박순은 받지 않는다. 이 목록은 판매 시작 시각이 없는 공연도 거르지 않아 정렬 키가 null일 수 있다. 공연 임박순은 정렬할 때 시작일이 오늘 이후인 공연만 남기므로 키가 있다. */
    private static final Set<ShowSort> SUPPORTED_SORTS =
            Set.of(ShowSort.POPULAR, ShowSort.LATEST, ShowSort.SHOW_START_APPROACHING);

    private final ShowQuerydslRepository showQuerydslRepository;
    private final ShowRepository showRepository;
    private final VenueLookupApi venueLookupApi;
    private final ShowCardImagePathConverter showCardImagePathConverter;

    public record Input(ShowListParam param, int size, ShowSort sort) {
        public Input {
            param = requireProvided(param, "param");
            sort = requireProvided(sort, "sort").requireOneOf(SUPPORTED_SORTS);
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
                input.param(), venueIdsOf(input.param().region()), input.size(), input.sort());
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
    private @Nullable Set<Long> venueIdsOf(final @Nullable String region) {
        return region == null ? null : venueLookupApi.findIdsByRegion(region);
    }

    private ShowResponse toResponse(
            final Show show, final Map<Long, List<String>> genreNames, final Map<Long, VenueSnapshot> venuesById) {
        final VenueSnapshot venue = venuesById.get(show.getVenueId());
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getSubTitle(),
                showCardImagePathConverter.toCardImage(show.getImage()),
                genreNames.getOrDefault(show.getId(), List.of()),
                show.getStartDate(),
                show.getEndDate(),
                show.getViewCount(),
                show.getDisplaySaleType(),
                show.getDisplaySaleStartsAt(),
                show.getDisplaySaleEndsAt(),
                show.getCreatedAt(),
                Optional.ofNullable(venue)
                        .map(VenueSnapshot::region)
                        .map(VenueSnapshot.RegionView::code)
                        .orElse(null),
                Optional.ofNullable(venue).map(VenueSnapshot::name).orElse(null));
    }
}
