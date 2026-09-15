package com.ticket.show.infrastructure;

import static com.ticket.show.domain.QCategory.category;
import static com.ticket.show.domain.QGenre.genre;
import static com.ticket.show.domain.show.QShow.show;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.querydsl.core.types.dsl.BooleanExpression;

/**
 * Show 쿼리에서 공통으로 사용되는 Querydsl 술어(predicate) 조립기다.
 *
 * <p>값이 없는 조건은 {@code null}을 돌려준다 — Querydsl의 {@code where}/{@code and}가 {@code null}을 "조건 없음"으로
 * 건너뛰므로, 호출부가 조건 유무를 따로 분기하지 않아도 된다.
 */
@Component
public class QuerydslShowPredicates {
    public @Nullable BooleanExpression categoryCodeEq(final @Nullable String categoryCode) {
        return StringUtils.hasText(categoryCode) ? category.code.eq(categoryCode) : null;
    }

    public @Nullable BooleanExpression genreEq(final @Nullable String genreCode) {
        return StringUtils.hasText(genreCode) ? genre.code.eq(genreCode) : null;
    }

    /**
     * region이 venueId 집합으로 이미 해석된 상태로 들어온다({@code QuerydslShowConditionBuilder}가 {@code
     * VenueLookupApi.findIdsByRegion}로 해석한다) — show는 venue module의 Region entity를 직접 참조하지 않는다. 빈
     * 집합이면 아무 결과도 없어야 하므로 {@code false}에 해당하는 술어를 돌려준다(Querydsl은 {@code path.in(빈 컬렉션)}을 {@code 1 =
     * 2}로 직렬화한다).
     */
    public BooleanExpression venueIdIn(final Set<Long> venueIds) {
        return show.venueId.in(venueIds);
    }

    public @Nullable BooleanExpression titleContains(final @Nullable String title) {
        return StringUtils.hasText(title) ? show.title.containsIgnoreCase(title) : null;
    }

    public @Nullable BooleanExpression keywordContains(final @Nullable String keyword) {
        return titleContains(keyword);
    }

    public @Nullable BooleanExpression displaySaleStartsAtGoe(final @Nullable LocalDateTime from) {
        return from != null ? show.displaySaleWindow.startsAt.goe(from) : null;
    }

    public @Nullable BooleanExpression displaySaleStartsAtLoe(final @Nullable LocalDateTime to) {
        return to != null ? show.displaySaleWindow.startsAt.loe(to) : null;
    }

    public @Nullable BooleanExpression displaySaleEndsAtGoe(final @Nullable LocalDateTime from) {
        return from != null ? show.displaySaleWindow.endsAt.goe(from) : null;
    }

    public @Nullable BooleanExpression displaySaleEndsAtLoe(final @Nullable LocalDateTime to) {
        return to != null ? show.displaySaleWindow.endsAt.loe(to) : null;
    }

    public @Nullable BooleanExpression startDateGoe(final @Nullable LocalDate from) {
        return from != null ? show.startDate.goe(from) : null;
    }

    public @Nullable BooleanExpression startDateLoe(final @Nullable LocalDate to) {
        return to != null ? show.startDate.loe(to) : null;
    }
}
