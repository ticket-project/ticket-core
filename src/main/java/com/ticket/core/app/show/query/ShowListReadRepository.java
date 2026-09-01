package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.core.app.show.query.model.ShowCursor;
import com.ticket.core.app.show.query.model.ShowListItemView;
import com.ticket.core.app.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.core.app.show.query.model.ShowOpeningSoonSummaryView;
import com.ticket.core.app.show.query.model.ShowParam;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import com.ticket.core.app.show.query.model.ShowSearchItemView;
import com.ticket.core.app.show.query.model.ShowSummaryView;
import com.ticket.core.app.support.cursor.CursorPage;

import java.util.List;

/**
 * 공연 목록/검색 읽기 전용 조회 포트다.
 *
 * <p>계약에는 app이 소유한 read model과 타입 커서 위치만 노출한다. Spring Data 타입과
 * HTTP 커서 문자열은 이 경계를 넘지 않는다.
 */
public interface ShowListReadRepository {

    CursorPage<ShowListItemView, ShowCursor> findAllBySearch(ShowParam param, int size, ShowSort sort);

    List<ShowSummaryView> findLatestShows(String categoryCode, int limit);

    List<ShowOpeningSoonSummaryView> findShowsSaleOpeningSoon(String categoryCode, int limit);

    CursorPage<ShowOpeningSoonDetailView, ShowCursor> findSaleOpeningSoonPage(
            SaleOpeningSoonSearchParam param,
            int size,
            ShowSort sort
    );

    CursorPage<ShowSearchItemView, ShowCursor> searchShows(ShowSearchCriteria request, int size, ShowSort sort);

    long countSearchShows(ShowSearchCriteria request);
}
