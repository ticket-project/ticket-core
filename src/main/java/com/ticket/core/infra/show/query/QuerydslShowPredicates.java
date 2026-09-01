package com.ticket.core.infra.show.query;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ticket.core.domain.show.model.Region;
import com.ticket.core.domain.show.model.BookingStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.ticket.core.domain.show.model.QCategory.category;
import static com.ticket.core.domain.show.model.QGenre.genre;
import static com.ticket.core.domain.show.model.QShow.show;

/**
 * Show 쿼리에서 공통으로 사용되는 Querydsl 술어(predicate) 조립기다.
 */
@Component
public class QuerydslShowPredicates {

    public BooleanExpression categoryCodeEq(final String categoryCode) {
        return StringUtils.hasText(categoryCode) ? category.code.eq(categoryCode) : null;
    }

    public BooleanExpression genreEq(final String genreCode) {
        return StringUtils.hasText(genreCode) ? genre.code.eq(genreCode) : null;
    }

    public BooleanExpression regionEq(final Region region) {
        return region != null ? show.venue.region.eq(region) : null;
    }

    public BooleanExpression titleContains(final String title) {
        return StringUtils.hasText(title) ? show.title.containsIgnoreCase(title) : null;
    }

    public BooleanExpression keywordContains(final String keyword) {
        return titleContains(keyword);
    }

    public BooleanExpression saleStartDateGoe(final LocalDateTime from) {
        return from != null ? show.saleStartDate.goe(from) : null;
    }

    public BooleanExpression saleStartDateLoe(final LocalDateTime to) {
        return to != null ? show.saleStartDate.loe(to) : null;
    }

    public BooleanExpression saleEndDateGoe(final LocalDateTime from) {
        return from != null ? show.saleEndDate.goe(from) : null;
    }

    public BooleanExpression saleEndDateLoe(final LocalDateTime to) {
        return to != null ? show.saleEndDate.loe(to) : null;
    }

    public BooleanExpression startDateGoe(final LocalDate from) {
        return from != null ? show.startDate.goe(from) : null;
    }

    public BooleanExpression startDateLoe(final LocalDate to) {
        return to != null ? show.startDate.loe(to) : null;
    }
}
