package com.ticket.core.app.show.query;

import com.ticket.core.app.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.core.app.show.query.model.ShowListItemView;
import com.ticket.core.app.show.query.model.ShowOpeningSoonDetailView;
import com.ticket.core.app.show.query.model.ShowOpeningSoonSummaryView;
import com.ticket.core.app.show.query.model.ShowParam;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import com.ticket.core.app.show.query.model.ShowSearchItemView;
import com.ticket.core.app.show.query.model.ShowSummaryView;
import com.ticket.core.app.support.cursor.CursorSlice;

import java.util.List;

public interface ShowListReadRepository {

    CursorSlice<ShowListItemView> findAllBySearch(ShowParam param, int size, String sort);

    List<ShowSummaryView> findLatestShows(String categoryCode, int limit);

    List<ShowOpeningSoonSummaryView> findShowsSaleOpeningSoon(String categoryCode, int limit);

    CursorSlice<ShowOpeningSoonDetailView> findSaleOpeningSoonPage(
            SaleOpeningSoonSearchParam param,
            int size,
            String sort
    );

    CursorSlice<ShowSearchItemView> searchShows(ShowSearchCriteria request, int size, String sort);

    long countSearchShows(ShowSearchCriteria request);
}
