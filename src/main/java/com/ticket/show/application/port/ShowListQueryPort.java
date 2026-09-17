package com.ticket.show.application.port;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.ticket.shared.api.CursorPage;
import com.ticket.show.application.query.LatestShowRow;
import com.ticket.show.application.query.SaleOpeningSoonDetailRow;
import com.ticket.show.application.query.SaleOpeningSoonSearchParam;
import com.ticket.show.application.query.SaleOpeningSoonSummaryRow;
import com.ticket.show.application.query.ShowCursor;
import com.ticket.show.application.query.ShowListItemRow;
import com.ticket.show.application.query.ShowListParam;
import com.ticket.show.application.query.ShowSearchCriteria;
import com.ticket.show.application.query.ShowSearchItemRow;
import com.ticket.show.application.query.ShowSort;

/**
 * 공연 목록/검색 읽기 전용 조회 포트다. show 자기 DB만 본다 — 반환 타입은 전부 {@code venueId} scalar만 담는 raw row이고, venue 표시값
 * 조합은 이 포트를 부르는 use case(application)가 한다.
 *
 * <p>계약에는 app이 소유한 read model과 타입 커서 위치만 노출한다. Spring Data 타입과 HTTP 커서 문자열은 이 경계를 넘지 않는다.
 *
 * <p>지역 조건은 {@code venueIds}로 이미 해석돼 들어온다({@link
 * com.ticket.show.application.query.RegionVenueIds}). {@code null}은 지역 조건 없음이고, <b>빈 집합은 조건은 있으나 해당
 * 공연장이 없다는 뜻이라 결과가 0건</b>이다 — 둘을 같게 다루면 안 된다.
 */
public interface ShowListQueryPort {
    CursorPage<ShowListItemRow, ShowCursor> findAllBySearch(
            ShowListParam param, @Nullable Set<Long> venueIds, int size, ShowSort sort);

    List<LatestShowRow> findLatestShows(String categoryCode, int limit);

    List<SaleOpeningSoonSummaryRow> findSaleOpeningSoonSummaries(String categoryCode, int limit);

    CursorPage<SaleOpeningSoonDetailRow, ShowCursor> findSaleOpeningSoonPage(
            SaleOpeningSoonSearchParam param,
            @Nullable Set<Long> venueIds,
            int size,
            ShowSort sort);

    CursorPage<ShowSearchItemRow, ShowCursor> searchShows(
            ShowSearchCriteria criteria, @Nullable Set<Long> venueIds, int size, ShowSort sort);

    long countSearchShows(ShowSearchCriteria criteria, @Nullable Set<Long> venueIds);
}
