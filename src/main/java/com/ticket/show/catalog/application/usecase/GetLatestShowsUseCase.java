package com.ticket.show.catalog.application.usecase;

import com.ticket.show.catalog.application.LatestShowRow;
import com.ticket.show.catalog.application.VenueDisplays;

import com.ticket.show.catalog.application.port.ShowListQueryPort;

import com.ticket.show.catalog.application.ShowSummaryView;
import com.ticket.venue.VenueLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetLatestShowsUseCase {
    public static final int LATEST_SHOWS_MAX_COUNT = 10;
    private final ShowListQueryPort showListQueryPort;
    private final VenueLookup venueLookup;

    public record Input(String category) {
    }

    public record Output(List<ShowSummaryView> shows) {
    }

    public Output execute(final Input input) {
        final List<LatestShowRow> rows = showListQueryPort.findLatestShows(input.category(), LATEST_SHOWS_MAX_COUNT);
        final VenueDisplays venues = VenueDisplays.load(venueLookup, rows.stream().map(LatestShowRow::venueId).toList());
        return new Output(rows.stream().map(row -> toView(row, venues)).toList());
    }

    private ShowSummaryView toView(final LatestShowRow row, final VenueDisplays venues) {
        return new ShowSummaryView(
                row.id(),
                row.title(),
                row.image(),
                row.startDate(),
                row.endDate(),
                venues.nameOf(row.venueId()),
                row.createdAt()
        );
    }
}
