package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requireProvided;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.api.CursorPage;
import com.ticket.show.domain.show.Show;
import com.ticket.show.persistence.ShowQuerydslRepository;
import com.ticket.venue.api.VenueLookupApi;
import com.ticket.venue.api.VenueSnapshot;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchShowsUseCase {
    private final ShowQuerydslRepository showQuerydslRepository;
    private final VenueLookupApi venueLookupApi;

    public record Input(ShowSearchCriteria criteria, int size, ShowSort sort) {
        public Input {
            criteria = requireProvided(criteria, "request");
            sort = requireProvided(sort, "sort").requireOneOf(ShowSort.WITHOUT_SALE_START);
            ShowPageSize.require(size);
        }
    }

    public record Output(
            List<ShowResponse> items,
            boolean hasNext,
            @Nullable ShowCursor nextPosition) {}

    public record ShowResponse(
            Long id,
            @Nullable String title,
            @Nullable String image,
            @Nullable String venue,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            @Nullable String region,
            long viewCount) {}

    public Output execute(final Input input) {
        final CursorPage<Show, ShowCursor> page = showQuerydslRepository.searchShows(
                input.criteria(),
                ShowListParam.venueIdsIn(venueLookupApi, input.criteria().region()),
                input.size(),
                input.sort());
        final Map<Long, VenueSnapshot> venuesById = venueLookupApi.getSummaries(
                Set.copyOf(page.items().stream().map(Show::getVenueId).toList()));
        final CursorPage<ShowResponse, ShowCursor> view = page.map(show -> toResponse(show, venuesById));
        return new Output(view.items(), view.hasNext(), view.nextPosition());
    }

    private ShowResponse toResponse(final Show show, final Map<Long, VenueSnapshot> venuesById) {
        final VenueSnapshot venue = venuesById.get(show.getVenueId());
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                ShowCardImagePathConverter.toCardImage(show.getImage()),
                venue == null ? null : venue.name(),
                show.getStartDate(),
                show.getEndDate(),
                venue == null ? null : venue.regionCode(),
                show.getViewCount());
    }
}
