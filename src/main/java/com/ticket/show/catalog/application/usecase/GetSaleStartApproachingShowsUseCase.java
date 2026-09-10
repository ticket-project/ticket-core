package com.ticket.show.catalog.application.usecase;

import com.ticket.show.catalog.application.SaleOpeningSoonSummaryRow;
import com.ticket.show.catalog.application.VenueDisplays;

import com.ticket.show.catalog.application.port.ShowListQueryPort;

import com.ticket.show.catalog.application.ShowOpeningSoonSummaryView;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.venue.VenueLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleStartApproachingShowsUseCase {
    private final ShowListQueryPort showListQueryPort;
    private final VenueLookup venueLookup;

    public record Input(String category, int size) {
        public Input {
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(List<ShowOpeningSoonSummaryView> shows) {
    }

    public Output execute(final Input input) {
        final List<SaleOpeningSoonSummaryRow> rows =
                showListQueryPort.findShowsSaleOpeningSoon(input.category(), input.size());
        final VenueDisplays venues = VenueDisplays.load(venueLookup, rows.stream().map(SaleOpeningSoonSummaryRow::venueId).toList());
        return new Output(rows.stream().map(row -> toView(row, venues)).toList());
    }

    private ShowOpeningSoonSummaryView toView(final SaleOpeningSoonSummaryRow row, final VenueDisplays venues) {
        return new ShowOpeningSoonSummaryView(
                row.id(),
                row.title(),
                row.image(),
                venues.nameOf(row.venueId()),
                row.displaySaleStartsAt()
        );
    }
}
