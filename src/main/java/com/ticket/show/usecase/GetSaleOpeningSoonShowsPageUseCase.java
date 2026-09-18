package com.ticket.show.usecase;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.persistence.ShowQueryRepository;
import com.ticket.show.query.SaleOpeningSoonDetailRow;
import com.ticket.show.query.SaleOpeningSoonDetailView;
import com.ticket.show.query.SaleOpeningSoonSearchParam;
import com.ticket.show.query.ShowCursor;
import com.ticket.show.query.ShowSort;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetSaleOpeningSoonShowsPageUseCase {
    private final ShowQueryRepository showQueryRepository;
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
            List<SaleOpeningSoonDetailView> items,
            boolean hasNext,
            @Nullable ShowCursor nextPosition) {}

    public Output execute(final Input input) {
        final CursorPage<SaleOpeningSoonDetailRow, ShowCursor> page =
                showQueryRepository.findSaleOpeningSoonPage(
                        input.param(),
                        venueIdsOf(input.param().getRegion()),
                        input.size(),
                        input.sort());
        final VenueDisplays venues =
                VenueDisplays.load(
                        venueLookup,
                        page.items().stream().map(SaleOpeningSoonDetailRow::venueId).toList());
        final CursorPage<SaleOpeningSoonDetailView, ShowCursor> view =
                page.map(row -> toView(row, venues));
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
