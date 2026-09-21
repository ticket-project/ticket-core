package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requireProvided;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.domain.show.Show;
import com.ticket.show.domain.show.ShowCardImagePathConverter;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchShowsUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookup;
    private final ShowCardImagePathConverter showCardImagePathConverter;

    public record Input(ShowSearchCriteria criteria, int size, ShowSort sort) {
        public Input {
            criteria = requireProvided(criteria, "request");
            sort = requireProvided(sort, "sort");
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(List<Item> items, boolean hasNext, @Nullable ShowCursor nextPosition) {}

    public record Item(
            Long id,
            @Nullable String title,
            @Nullable String image,
            @Nullable String venue,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable Region region,
            long viewCount) {}

    public Output execute(final Input input) {
        final CursorPage<Show, ShowCursor> page =
                showQuerydslRepository.searchShows(
                        input.criteria(),
                        venueIdsOf(input.criteria().getRegion()),
                        input.size(),
                        input.sort());
        final VenueDisplays venues =
                VenueDisplays.load(
                        venueLookup, page.items().stream().map(Show::getVenueId).toList());
        final CursorPage<Item, ShowCursor> view = page.map(show -> toItem(show, venues));
        return new Output(view.items(), view.hasNext(), view.nextPosition());
    }

    /**
     * 지역 조건을 venueId 집합으로 해석한다.
     *
     * <p><b>"지역 없음"과 "지역은 있으나 그 지역에 공연장이 없음"은 다른 결과다.</b> 그래서 {@code null}(지역 조건 자체가 없음)과 빈 집합(조건은
     * 있는데 해당 공연장이 없으니 결과 0건)을 구분해 넘긴다. 이 둘을 뭉개면 "제주에 공연장이 하나도 없다"가 "전체 목록"으로 조용히 바뀐다.
     */
    private @Nullable Set<Long> venueIdsOf(final @Nullable Region region) {
        return region == null ? null : venueLookup.findIdsByRegion(region);
    }

    private Item toItem(final Show show, final VenueDisplays venues) {
        return new Item(
                show.getId(),
                show.getTitle(),
                showCardImagePathConverter.toCardImage(show.getImage()),
                venues.nameOf(show.getVenueId()),
                show.getStartDate(),
                show.getEndDate(),
                venues.regionOf(show.getVenueId()),
                show.getViewCount());
    }
}
