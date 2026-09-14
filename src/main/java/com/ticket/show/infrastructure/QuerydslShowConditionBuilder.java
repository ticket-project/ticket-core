package com.ticket.show.infrastructure;

import static com.ticket.show.domain.show.QShow.show;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.querydsl.core.BooleanBuilder;
import com.ticket.show.application.SaleOpeningSoonSearchParam;
import com.ticket.show.application.ShowListParam;
import com.ticket.show.application.ShowSearchCriteria;
import com.ticket.show.application.ShowSort;
import com.ticket.show.infrastructure.QuerydslShowSortResolver.SortOrder;
import com.ticket.venue.api.VenueLookupApi;

import lombok.RequiredArgsConstructor;

/**
 * 공연 목록/검색 use case별로 {@link QuerydslShowPredicates}와 {@link SaleDisplayStatusPredicateFactory}의 개별
 * 술어를 조합해 완성된 WHERE 조건을 만든다.
 */
@Component
@RequiredArgsConstructor
public class QuerydslShowConditionBuilder {
    private final QuerydslShowPredicates showPredicates;
    private final SaleDisplayStatusPredicateFactory saleDisplayStatusPredicateFactory;
    private final VenueLookupApi venueLookup;
    private final Clock clock;

    public BooleanBuilder buildMainListCondition(
            final ShowListParam param, final SortOrder sortOrder) {
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
        where.and(show.displaySaleWindow.startsAt.goe(LocalDateTime.now(clock)));
        return where;
    }

    public BooleanBuilder buildSaleOpeningSoonCondition(final SaleOpeningSoonSearchParam param) {
        final BooleanBuilder where = new BooleanBuilder();
        where.and(show.displaySaleWindow.startsAt.goe(LocalDateTime.now(clock)));
        where.and(showPredicates.categoryCodeEq(param.getCategory()));
        appendRegionCondition(where, param.getRegion());
        where.and(showPredicates.titleContains(param.getTitle()));
        where.and(showPredicates.displaySaleStartsAtGoe(param.getDisplaySaleStartsAtFrom()));
        where.and(showPredicates.displaySaleStartsAtLoe(param.getDisplaySaleStartsAtTo()));
        where.and(showPredicates.displaySaleEndsAtGoe(param.getDisplaySaleEndsAtFrom()));
        where.and(showPredicates.displaySaleEndsAtLoe(param.getDisplaySaleEndsAtTo()));
        return where;
    }

    public BooleanBuilder buildSearchCondition(
            final ShowSearchCriteria criteria, final SortOrder sortOrder) {
        final BooleanBuilder where = new BooleanBuilder();
        final LocalDateTime now = LocalDateTime.now(clock);
        where.and(showPredicates.keywordContains(criteria.getKeyword()));
        where.and(showPredicates.categoryCodeEq(criteria.getCategory()));
        appendRegionCondition(where, criteria.getRegion());
        where.and(showPredicates.startDateGoe(criteria.getStartDateFrom()));
        where.and(showPredicates.startDateLoe(criteria.getStartDateTo()));
        where.and(
                saleDisplayStatusPredicateFactory.condition(criteria.getSaleDisplayStatus(), now));
        appendShowStartApproachingCondition(where, sortOrder, now.toLocalDate());
        return where;
    }

    /**
     * region 검색 조건을 venueId 집합으로 해석해 붙인다. show는 venue module의 Region entity를 직접 참조하지 않는다 — {@code
     * VenueLookupApi.findIdsByRegion}로 얻은 venueId 집합에 대해서만 {@code show.venueId.in(...)}을 건다.
     */
    private void appendRegionCondition(
            final BooleanBuilder where, final com.ticket.venue.api.Region region) {
        if (region != null) {
            where.and(showPredicates.venueIdIn(venueLookup.findIdsByRegion(region)));
        }
    }

    private void appendShowStartApproachingCondition(
            final BooleanBuilder where, final SortOrder sortOrder, final LocalDate today) {
        if (sortOrder != null && ShowSort.SHOW_START_APPROACHING.equals(sortOrder.key())) {
            where.and(show.startDate.goe(today));
        }
    }
}
