package com.ticket.show.infrastructure.show.query;

import com.querydsl.core.BooleanBuilder;
import com.ticket.show.application.show.query.ShowSort;
import com.ticket.show.infrastructure.show.query.QuerydslShowSortResolver.SortOrder;
import com.ticket.show.application.show.query.model.SaleOpeningSoonSearchParam;
import com.ticket.show.application.show.query.model.ShowParam;
import com.ticket.show.application.show.query.model.ShowSearchCriteria;
import com.ticket.venue.VenueLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.ticket.show.domain.show.QShow.show;

/**
 * 공연 목록/검색 use case별로 {@link QuerydslShowPredicates}와
 * {@link BookingStatusPredicateFactory}의 개별 술어를 조합해 완성된 WHERE 조건을 만든다.
 */
@Component
@RequiredArgsConstructor
public class QuerydslShowConditionBuilder {

    private final QuerydslShowPredicates showPredicates;
    private final BookingStatusPredicateFactory bookingStatusPredicateFactory;
    private final VenueLookup venueLookup;
    private final Clock clock;

    public BooleanBuilder buildMainListCondition(final ShowParam param, final SortOrder sortOrder) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(showPredicates.categoryCodeEq(param.getCategory()));
        appendRegionCondition(where, param.getRegion());
        where.and(showPredicates.genreEq(param.getGenre()));
        appendShowStartApproachingCondition(where, sortOrder, LocalDate.now(clock));
        return where;
    }

    public BooleanBuilder buildSaleOpeningSoonSummaryCondition(final String categoryCode) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(showPredicates.categoryCodeEq(categoryCode));
        where.and(show.saleStartDate.goe(LocalDateTime.now(clock)));
        return where;
    }

    public BooleanBuilder buildSaleOpeningCondition(final SaleOpeningSoonSearchParam param) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(show.saleStartDate.goe(LocalDateTime.now(clock)));
        where.and(showPredicates.categoryCodeEq(param.getCategory()));
        appendRegionCondition(where, param.getRegion());
        where.and(showPredicates.titleContains(param.getTitle()));
        where.and(showPredicates.saleStartDateGoe(param.getSaleStartDateFrom()));
        where.and(showPredicates.saleStartDateLoe(param.getSaleStartDateTo()));
        where.and(showPredicates.saleEndDateGoe(param.getSaleEndDateFrom()));
        where.and(showPredicates.saleEndDateLoe(param.getSaleEndDateTo()));
        return where;
    }

    public BooleanBuilder buildSearchCondition(final ShowSearchCriteria request, final SortOrder sortOrder) {
        final BooleanBuilder where = new BooleanBuilder();
        final LocalDateTime now = LocalDateTime.now(clock);
        where.and(showPredicates.keywordContains(request.getKeyword()));
        where.and(showPredicates.categoryCodeEq(request.getCategory()));
        appendRegionCondition(where, request.getRegion());
        where.and(showPredicates.startDateGoe(request.getStartDateFrom()));
        where.and(showPredicates.startDateLoe(request.getStartDateTo()));
        where.and(bookingStatusPredicateFactory.condition(request.getBookingStatus(), now));
        appendShowStartApproachingCondition(where, sortOrder, now.toLocalDate());
        return where;
    }

    /**
     * region 검색 조건을 venueId 집합으로 해석해 붙인다. show는 venue module의 Region entity를
     * 직접 참조하지 않는다 — {@code VenueLookup.findIdsByRegion}로 얻은 venueId 집합에 대해서만
     * {@code show.venueId.in(...)}을 건다.
     */
    private void appendRegionCondition(final BooleanBuilder where, final com.ticket.venue.Region region) {
        if (region != null) {
            where.and(showPredicates.venueIdIn(venueLookup.findIdsByRegion(region)));
        }
    }

    private void appendShowStartApproachingCondition(
            final BooleanBuilder where,
            final SortOrder sortOrder,
            final LocalDate today
    ) {
        if (sortOrder != null && ShowSort.SHOW_START_APPROACHING.equals(sortOrder.key())) {
            where.and(show.startDate.goe(today));
        }
    }
}
