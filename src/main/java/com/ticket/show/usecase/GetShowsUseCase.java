package com.ticket.show.usecase;

import static com.ticket.shared.api.InputChecks.requireProvided;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket.shared.api.CursorPage;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.persistence.ShowQueryRepository;
import com.ticket.show.query.ShowCursor;
import com.ticket.show.query.ShowListItemRow;
import com.ticket.show.query.ShowListParam;
import com.ticket.show.query.ShowSort;
import com.ticket.show.usecase.view.ShowListItemView;
import com.ticket.venue.api.Region;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetShowsUseCase {
    /** API 문서가 공개한 상한이다(`한 번에 조회할 개수 (기본값: 5, 최대: 100)`). 상한은 유스케이스 조건이므로 API가 아니라 여기가 소유한다. */
    public static final int MAX_SIZE = 100;

    private final ShowQueryRepository showQueryRepository;
    private final VenueLookupApi venueLookup;

    public record Input(ShowListParam param, int size, ShowSort sort) {
        public Input {
            param = requireProvided(param, "param");
            sort = requireProvided(sort, "sort");
            if (size <= 0 || size > MAX_SIZE) {
                throw new InvalidRequestException("size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
            }
        }
    }

    public record Output(
            List<ShowListItemView> items, boolean hasNext, @Nullable ShowCursor nextPosition) {}

    public Output execute(final Input input) {
        final CursorPage<ShowListItemRow, ShowCursor> page =
                showQueryRepository.findAllBySearch(
                        input.param(),
                        venueIdsOf(input.param().getRegion()),
                        input.size(),
                        input.sort());
        final VenueDisplays venues =
                VenueDisplays.load(
                        venueLookup, page.items().stream().map(ShowListItemRow::venueId).toList());
        final CursorPage<ShowListItemView, ShowCursor> view = page.map(row -> toView(row, venues));
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
