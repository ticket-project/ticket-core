package com.ticket.show.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.query.SaleOpeningSoonSummaryRow;
import com.ticket.show.query.SaleOpeningSoonSummaryView;
import com.ticket.show.query.ShowListQuery;
import com.ticket.show.query.VenueDisplays;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleOpeningSoonShowsUseCase {
    private final ShowListQuery showListQuery;
    private final VenueLookupApi venueLookup;

    public record Input(String category, int size) {
        public Input {
            if (size <= 0) {
                throw new InvalidRequestException("size는 1 이상이어야 합니다.");
            }
        }
    }

    public record Output(List<SaleOpeningSoonSummaryView> shows) {}

    public Output execute(final Input input) {
        final List<SaleOpeningSoonSummaryRow> rows =
                showListQuery.findSaleOpeningSoonSummaries(input.category(), input.size());
        final VenueDisplays venues =
                VenueDisplays.load(
                        venueLookup,
                        rows.stream().map(SaleOpeningSoonSummaryRow::venueId).toList());
        return new Output(rows.stream().map(row -> toView(row, venues)).toList());
    }

    private SaleOpeningSoonSummaryView toView(
            final SaleOpeningSoonSummaryRow row, final VenueDisplays venues) {
        return new SaleOpeningSoonSummaryView(
                row.id(),
                row.title(),
                row.image(),
                venues.nameOf(row.venueId()),
                row.displaySaleStartsAt());
    }
}
