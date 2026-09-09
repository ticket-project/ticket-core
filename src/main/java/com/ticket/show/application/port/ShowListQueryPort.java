package com.ticket.show.application.port;

import com.ticket.show.application.LatestShowRow;
import com.ticket.show.application.SaleOpeningSoonDetailRow;
import com.ticket.show.application.SaleOpeningSoonSearchParam;
import com.ticket.show.application.SaleOpeningSoonSummaryRow;
import com.ticket.show.application.ShowCursor;
import com.ticket.show.application.ShowListItemRow;
import com.ticket.show.application.ShowParam;
import com.ticket.show.application.ShowSearchCriteria;
import com.ticket.show.application.ShowSearchItemRow;
import com.ticket.show.application.ShowSort;
import com.ticket.shared.CursorPage;

import java.util.List;

/**
 * 공연 목록/검색 읽기 전용 조회 포트다. show 자기 DB만 본다 — 반환 타입은 전부 {@code venueId}
 * scalar만 담는 raw row이고, venue 표시값 조합은 이 포트를 부르는 use case(application)가 한다.
 *
 * <p>계약에는 app이 소유한 read model과 타입 커서 위치만 노출한다. Spring Data 타입과
 * HTTP 커서 문자열은 이 경계를 넘지 않는다.
 */
public interface ShowListQueryPort {

    CursorPage<ShowListItemRow, ShowCursor> findAllBySearch(ShowParam param, int size, ShowSort sort);

    List<LatestShowRow> findLatestShows(String categoryCode, int limit);

    List<SaleOpeningSoonSummaryRow> findShowsSaleOpeningSoon(String categoryCode, int limit);

    CursorPage<SaleOpeningSoonDetailRow, ShowCursor> findSaleOpeningSoonPage(
            SaleOpeningSoonSearchParam param,
            int size,
            ShowSort sort
    );

    CursorPage<ShowSearchItemRow, ShowCursor> searchShows(ShowSearchCriteria request, int size, ShowSort sort);

    long countSearchShows(ShowSearchCriteria request);
}
