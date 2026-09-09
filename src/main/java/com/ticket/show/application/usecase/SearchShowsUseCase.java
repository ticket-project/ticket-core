package com.ticket.show.application.usecase;

import com.ticket.show.application.ShowSearchItemRow;
import com.ticket.show.application.ShowSort;
import com.ticket.show.application.VenueDisplays;

import com.ticket.show.application.port.ShowListQueryPort;

import com.ticket.show.application.ShowCursor;
import com.ticket.show.application.ShowSearchCriteria;
import com.ticket.show.application.ShowSearchItemView;
import com.ticket.error.InvalidRequestException;
import com.ticket.shared.CursorPage;
import com.ticket.venue.VenueLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchShowsUseCase {
    private final ShowListQueryPort showListQueryPort;
    private final VenueLookup venueLookup;

    public record Input(ShowSearchCriteria request, int size, ShowSort sort) {
        public Input {
            if (request == null) {
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

    public record Output(List<ShowSearchItemView> items, boolean hasNext, ShowCursor nextPosition) {
    }

    public Output execute(final Input input) {
        final CursorPage<ShowSearchItemRow, ShowCursor> page = showListQueryPort.searchShows(
                input.request(), input.size(), input.sort());
        final VenueDisplays venues = VenueDisplays.load(venueLookup, page.items().stream().map(ShowSearchItemRow::venueId).toList());
        final CursorPage<ShowSearchItemView, ShowCursor> view = page.map(row -> toView(row, venues));
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
                row.viewCount()
        );
    }
}
