package com.ticket.core.infra.show.query;

import com.querydsl.core.BooleanBuilder;
import com.ticket.core.domain.show.meta.ShowSortKey;
import com.ticket.core.infra.show.query.ShowSortSupport.SortOrder;
import com.ticket.core.app.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.core.app.show.query.model.ShowParam;
import com.ticket.core.app.show.query.model.ShowSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.ticket.core.domain.show.model.QShow.show;

@Component
@RequiredArgsConstructor
public class ShowConditionFactory {

    private final ShowQueryHelper showQueryHelper;
    private final BookingStatusWindowPolicy bookingStatusWindowPolicy;
    private final Clock clock;

    public BooleanBuilder buildMainListCondition(final ShowParam param, final SortOrder sortOrder) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(showQueryHelper.categoryCodeEq(param.getCategory()));
        where.and(showQueryHelper.regionEq(param.getRegion()));
        where.and(showQueryHelper.genreEq(param.getGenre()));
        appendShowStartApproachingCondition(where, sortOrder, LocalDate.now(clock));
        return where;
    }

    public BooleanBuilder buildSaleOpeningSoonSummaryCondition(final String categoryCode) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(showQueryHelper.categoryCodeEq(categoryCode));
        where.and(show.saleStartDate.goe(LocalDateTime.now(clock)));
        return where;
    }

    public BooleanBuilder buildSaleOpeningCondition(final SaleOpeningSoonSearchParam param) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(show.saleStartDate.goe(LocalDateTime.now(clock)));
        where.and(showQueryHelper.categoryCodeEq(param.getCategory()));
        where.and(showQueryHelper.regionEq(param.getRegion()));
        where.and(showQueryHelper.titleContains(param.getTitle()));
        where.and(showQueryHelper.saleStartDateGoe(param.getSaleStartDateFrom()));
        where.and(showQueryHelper.saleStartDateLoe(param.getSaleStartDateTo()));
        where.and(showQueryHelper.saleEndDateGoe(param.getSaleEndDateFrom()));
        where.and(showQueryHelper.saleEndDateLoe(param.getSaleEndDateTo()));
        return where;
    }

    public BooleanBuilder buildSearchCondition(final ShowSearchCriteria request, final SortOrder sortOrder) {
        final BooleanBuilder where = new BooleanBuilder();
        final LocalDateTime now = LocalDateTime.now(clock);
        where.and(showQueryHelper.keywordContains(request.getKeyword()));
        where.and(showQueryHelper.categoryCodeEq(request.getCategory()));
        where.and(showQueryHelper.regionEq(request.getRegion()));
        where.and(showQueryHelper.startDateGoe(request.getStartDateFrom()));
        where.and(showQueryHelper.startDateLoe(request.getStartDateTo()));
        where.and(bookingStatusWindowPolicy.condition(request.getBookingStatus(), now));
        appendShowStartApproachingCondition(where, sortOrder, now.toLocalDate());
        return where;
    }

    private void appendShowStartApproachingCondition(
            final BooleanBuilder where,
            final SortOrder sortOrder,
            final LocalDate today
    ) {
        if (sortOrder != null && ShowSortKey.SHOW_START_APPROACHING.equals(sortOrder.key())) {
            where.and(show.startDate.goe(today));
        }
    }
}
