package com.ticket.show.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.application.SaleOpeningSoonDetailRow;
import com.ticket.show.application.SaleOpeningSoonDetailView;
import com.ticket.show.application.SaleOpeningSoonSearchParam;
import com.ticket.show.application.ShowCursor;
import com.ticket.show.application.ShowSort;
import com.ticket.show.application.VenueDisplays;
import com.ticket.show.application.port.ShowListQueryPort;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleOpeningSoonShowsPageUseCase {
    private final ShowListQueryPort showListQueryPort;
    private final VenueLookupApi venueLookup;

    public record Input(SaleOpeningSoonSearchParam param, int size, ShowSort sort) {
        public Input {
            if (param == null) {
                throw new InvalidRequestException("param는 필수입니다.");
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
            List<SaleOpeningSoonDetailView> items, boolean hasNext, ShowCursor nextPosition) {}

    public Output execute(final Input input) {
        final CursorPage<SaleOpeningSoonDetailRow, ShowCursor> page =
                showListQueryPort.findSaleOpeningSoonPage(
                        input.param(), input.size(), input.sort());
        final VenueDisplays venues =
                VenueDisplays.load(
                        venueLookup,
                        page.items().stream().map(SaleOpeningSoonDetailRow::venueId).toList());
        final CursorPage<SaleOpeningSoonDetailView, ShowCursor> view =
                page.map(row -> toView(row, venues));
        return new Output(view.items(), view.hasNext(), view.nextPosition());
    }

    private SaleOpeningSoonDetailView toView(
            final SaleOpeningSoonDetailRow row, final VenueDisplays venues) {
        return new SaleOpeningSoonDetailView(
                row.id(),
                row.title(),
                row.subTitle(),
                row.image(),
                venues.nameOf(row.venueId()),
                venues.regionOf(row.venueId()),
                row.startDate(),
                row.endDate(),
                row.displaySaleStartsAt(),
                row.displaySaleEndsAt(),
                row.viewCount());
    }
}
