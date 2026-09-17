package com.ticket.show.usecase;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.query.RegionVenueIds;
import com.ticket.show.query.ShowCursor;
import com.ticket.show.query.ShowListQueryPort;
import com.ticket.show.query.ShowSearchCriteria;
import com.ticket.show.query.ShowSearchItemRow;
import com.ticket.show.query.ShowSearchItemView;
import com.ticket.show.query.ShowSort;
import com.ticket.show.query.VenueDisplays;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchShowsUseCase {
    private final ShowListQueryPort showListQueryPort;
    private final VenueLookupApi venueLookup;

    public record Input(ShowSearchCriteria criteria, int size, ShowSort sort) {
        public Input {
            if (criteria == null) {
                throw new InvalidRequestException("request는 필수입니다.");
            }
            if (sort == null) {
                throw new InvalidRequestException("sort는 필수입니다.");
            }
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(
            List<ShowSearchItemView> items, boolean hasNext, @Nullable ShowCursor nextPosition) {}

    public Output execute(final Input input) {
        final CursorPage<ShowSearchItemRow, ShowCursor> page =
                showListQueryPort.searchShows(
                        input.criteria(),
                        RegionVenueIds.resolve(venueLookup, input.criteria().getRegion()),
                        input.size(),
                        input.sort());
        final VenueDisplays venues =
                VenueDisplays.load(
                        venueLookup,
                        page.items().stream().map(ShowSearchItemRow::venueId).toList());
        final CursorPage<ShowSearchItemView, ShowCursor> view =
                page.map(row -> toView(row, venues));
        return new Output(view.items(), view.hasNext(), view.nextPosition());
    }

    private ShowSearchItemView toView(final ShowSearchItemRow row, final VenueDisplays venues) {
        return new ShowSearchItemView(
                row.id(),
                row.title(),
                row.image(),
                venues.nameOf(row.venueId()),
                row.startDate(),
                row.endDate(),
                venues.regionOf(row.venueId()),
                row.viewCount());
    }
}
