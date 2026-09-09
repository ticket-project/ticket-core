package com.ticket.show.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static com.ticket.show.domain.QCategory.category;
import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.QShow.show;

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

    /**
     * region이 venueId 집합으로 이미 해석된 상태로 들어온다({@code QuerydslShowConditionBuilder}가
     * {@code VenueLookup.findIdsByRegion}로 해석한다) — show는 venue module의 Region entity를
     * 직접 참조하지 않는다. 빈 집합이면 아무 결과도 없어야 하므로 {@code false}에 해당하는 술어를
     * 돌려준다(Querydsl은 {@code path.in(빈 컬렉션)}을 {@code 1 = 2}로 직렬화한다).
     */
    public BooleanExpression venueIdIn(final Set<Long> venueIds) {
        return show.venueId.in(venueIds);
    }

    public BooleanExpression titleContains(final String title) {
        return StringUtils.hasText(title) ? show.title.containsIgnoreCase(title) : null;
    }

    public BooleanExpression keywordContains(final String keyword) {
        return titleContains(keyword);
    }

    public BooleanExpression displaySaleStartsAtGoe(final LocalDateTime from) {
        return from != null ? show.displaySaleWindow.startsAt.goe(from) : null;
    }

    public BooleanExpression displaySaleStartsAtLoe(final LocalDateTime to) {
        return to != null ? show.displaySaleWindow.startsAt.loe(to) : null;
    }

    public BooleanExpression displaySaleEndsAtGoe(final LocalDateTime from) {
        return from != null ? show.displaySaleWindow.endsAt.goe(from) : null;
    }

    public BooleanExpression displaySaleEndsAtLoe(final LocalDateTime to) {
        return to != null ? show.displaySaleWindow.endsAt.loe(to) : null;
    }

    public BooleanExpression startDateGoe(final LocalDate from) {
        return from != null ? show.startDate.goe(from) : null;
    }

    public BooleanExpression startDateLoe(final LocalDate to) {
        return to != null ? show.startDate.loe(to) : null;
    }
}
