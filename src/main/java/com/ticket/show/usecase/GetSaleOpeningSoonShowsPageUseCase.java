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
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.Show;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleOpeningSoonShowsPageUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookup;
    private final ShowCardImagePathConverter showCardImagePathConverter;

    public record Input(SaleOpeningSoonSearchParam param, int size, ShowSort sort) {
        public Input {
            param = requireProvided(param, "param");
            sort = requireProvided(sort, "sort");
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(
            List<Item> items, boolean hasNext, @Nullable ShowCursor nextPosition) {}

    /**
     * 컴포넌트 이름은 {@code display} 어휘를 쓰지만(ADR 0007), 공개 API JSON 이름 {@code saleStartDate}/{@code saleEndDate}는 그대로 고정한다.
     */
    public record Item(
            Long id,
            @Nullable String title,
            @Nullable String subTitle,
            @Nullable String image,
            @Nullable String venue,
            @Nullable String region,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @JsonProperty("saleStartDate") @Nullable LocalDateTime displaySaleStartsAt,
            @JsonProperty("saleEndDate") @Nullable LocalDateTime displaySaleEndsAt,
            long viewCount) {}

    public Output execute(final Input input) {
        final CursorPage<Show, ShowCursor> page = showQuerydslRepository.findSaleOpeningSoonPage(
                input.param(), venueIdsOf(input.param().getRegion()), input.size(), input.sort());
        final Map<Long, VenueSnapshot> venuesById = venueLookup.getSummaries(
                Set.copyOf(page.items().stream().map(Show::getVenueId).toList()));
        final VenueDisplays venues = new VenueDisplays(venuesById);
        final CursorPage<Item, ShowCursor> view = page.map(show -> toItem(show, venues));
        return new Output(view.items(), view.hasNext(), view.nextPosition());
    }

    /**
     * 지역 조건을 venueId 집합으로 해석한다.
     *
     * <p><b>"지역 없음"과 "지역은 있으나 그 지역에 공연장이 없음"은 다른 결과다.</b> 그래서 {@code null}(지역 조건 자체가 없음)과 빈 집합(조건은 있는데 해당 공연장이 없으니 결과
     * 0건)을 구분해 넘긴다. 이 둘을 뭉개면 "제주에 공연장이 하나도 없다"가 "전체 목록"으로 조용히 바뀐다.
     */
    private @Nullable Set<Long> venueIdsOf(final @Nullable String region) {
        return region == null ? null : venueLookup.findIdsByRegion(region);
    }

    private Item toItem(final Show show, final VenueDisplays venues) {
        return new Item(
                show.getId(),
                show.getTitle(),
                show.getSubTitle(),
                showCardImagePathConverter.toCardImage(show.getImage()),
                venues.nameOf(show.getVenueId()),
                venues.regionCodeOf(show.getVenueId()),
                show.getStartDate(),
                show.getEndDate(),
                show.getDisplaySaleStartsAt(),
                show.getDisplaySaleEndsAt(),
                show.getViewCount());
    }
}
