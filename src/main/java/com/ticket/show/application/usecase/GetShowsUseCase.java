package com.ticket.show.application.usecase;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.application.port.ShowListQueryPort;
import com.ticket.show.application.query.RegionVenueIds;
import com.ticket.show.application.query.ShowCursor;
import com.ticket.show.application.query.ShowListItemRow;
import com.ticket.show.application.query.ShowListItemView;
import com.ticket.show.application.query.ShowListParam;
import com.ticket.show.application.query.ShowSort;
import com.ticket.show.application.query.VenueDisplays;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowsUseCase {
    /** API 문서가 공개한 상한이다(`한 번에 조회할 개수 (기본값: 5, 최대: 100)`). 상한은 유스케이스 조건이므로 API가 아니라 여기가 소유한다. */
    public static final int MAX_SIZE = 100;

    private final ShowListQueryPort showListQueryPort;
    private final VenueLookupApi venueLookup;

    public record Input(ShowListParam param, int size, ShowSort sort) {
        public Input {
            if (param == null) {
                throw new InvalidRequestException("param는 필수입니다.");
            }
            if (sort == null) {
                throw new InvalidRequestException("sort는 필수입니다.");
            }
            if (size <= 0 || size > MAX_SIZE) {
                throw new InvalidRequestException("size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
            }
        }
    }

    public record Output(
            List<ShowListItemView> items, boolean hasNext, @Nullable ShowCursor nextPosition) {}

    public Output execute(final Input input) {
        final CursorPage<ShowListItemRow, ShowCursor> page =
                showListQueryPort.findAllBySearch(
                        input.param(),
                        RegionVenueIds.resolve(venueLookup, input.param().getRegion()),
                        input.size(),
                        input.sort());
        final VenueDisplays venues =
                VenueDisplays.load(
                        venueLookup, page.items().stream().map(ShowListItemRow::venueId).toList());
        final CursorPage<ShowListItemView, ShowCursor> view = page.map(row -> toView(row, venues));
        return new Output(view.items(), view.hasNext(), view.nextPosition());
    }

    private ShowListItemView toView(final ShowListItemRow row, final VenueDisplays venues) {
        return new ShowListItemView(
                row.id(),
                row.title(),
                row.subTitle(),
                row.image(),
                row.genreNames(),
                row.startDate(),
                row.endDate(),
                row.viewCount(),
                row.displaySaleType(),
                row.displaySaleStartsAt(),
                row.displaySaleEndsAt(),
                row.createdAt(),
                venues.regionOf(row.venueId()),
                venues.nameOf(row.venueId()));
    }
}
