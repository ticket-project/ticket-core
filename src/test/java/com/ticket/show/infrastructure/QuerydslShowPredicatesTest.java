package com.ticket.show.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowPredicatesTest {

    private final QuerydslShowPredicates showQueryHelper = new QuerydslShowPredicates();

    @Test
    void 빈_문자열_필터는_null_조건을_반환한다() {
        //given
        //when
        //then
        assertThat(showQueryHelper.categoryCodeEq(" ")).isNull();
        assertThat(showQueryHelper.genreEq(" ")).isNull();
        assertThat(showQueryHelper.titleContains(" ")).isNull();
        assertThat(showQueryHelper.keywordContains(" ")).isNull();
    }

    @Test
    void 유효한_문자열_필터는_조건식을_반환한다() {
        //given
        //when
        //then
        assertThat(showQueryHelper.categoryCodeEq("CONCERT")).isNotNull();
        assertThat(showQueryHelper.genreEq("KPOP")).isNotNull();
        assertThat(showQueryHelper.titleContains("뮤지컬")).isNotNull();
        assertThat(showQueryHelper.keywordContains("뮤지컬")).isNotNull();
    }

    @Test
    void 날짜_필터는_null이면_null을_반환하고_값이_있으면_조건을_반환한다() {
        //given
        //when
        //then
        assertThat(showQueryHelper.displaySaleStartsAtGoe(null)).isNull();
        assertThat(showQueryHelper.displaySaleStartsAtLoe(null)).isNull();
        assertThat(showQueryHelper.displaySaleEndsAtGoe(null)).isNull();
        assertThat(showQueryHelper.displaySaleEndsAtLoe(null)).isNull();
        assertThat(showQueryHelper.startDateGoe(null)).isNull();
        assertThat(showQueryHelper.startDateLoe(null)).isNull();

        assertThat(showQueryHelper.displaySaleStartsAtGoe(LocalDateTime.of(2026, 3, 1, 0, 0))).isNotNull();
        assertThat(showQueryHelper.displaySaleStartsAtLoe(LocalDateTime.of(2026, 3, 31, 23, 59))).isNotNull();
        assertThat(showQueryHelper.displaySaleEndsAtGoe(LocalDateTime.of(2026, 4, 1, 0, 0))).isNotNull();
        assertThat(showQueryHelper.displaySaleEndsAtLoe(LocalDateTime.of(2026, 4, 30, 23, 59))).isNotNull();
        assertThat(showQueryHelper.startDateGoe(LocalDate.of(2026, 3, 1))).isNotNull();
        assertThat(showQueryHelper.startDateLoe(LocalDate.of(2026, 3, 31))).isNotNull();
    }

    /**
     * region 검색 조건은 Region이 아니라 이미 해석된 venueId 집합을 받는다
     * ({@code QuerydslShowConditionBuilder}가 {@code VenueLookup.findIdsByRegion}으로 해석한다).
     * 빈 집합이어도 {@code show.venueId.in(빈 집합)}은 항상 유효한 술어를 반환한다(Querydsl이
     * {@code 1 = 2}로 직렬화한다) — {@code regionEq}와 달리 null을 반환하지 않는다.
     */
    @Test
    void venueIdIn은_빈_집합이어도_null이_아닌_조건을_반환한다() {
        //given
        //when
        //then
        assertThat(showQueryHelper.venueIdIn(Set.of())).isNotNull();
        assertThat(showQueryHelper.venueIdIn(Set.of(1L, 2L))).isNotNull();
    }

}

