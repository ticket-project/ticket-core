package com.ticket.show.persistence.querydsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.querydsl.core.types.Order;
import com.ticket.shared.exception.InvalidRequestException;
import com.ticket.show.query.ShowCursor;
import com.ticket.show.query.ShowSort;

@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowSortResolverTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);
    private final Clock clock =
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.from(ZoneOffset.UTC));
    private final QuerydslShowSortResolver showSortSupport =
            new QuerydslShowSortResolver(new SaleDisplayStatusPredicates(), clock);

    @Test
    void 인기순은_desc_정렬을_사용한다() {
        // given
        // when
        QuerydslShowSortResolver.SortOrder result =
                showSortSupport.resolveSortOrder(ShowSort.POPULAR);
        // then
        assertThat(result.key()).isEqualTo(ShowSort.POPULAR);
        assertThat(result.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(showSortSupport.primaryOrderSpecifier(result).getOrder()).isEqualTo(Order.DESC);
        assertThat(showSortSupport.tieBreakerOrder(result).getOrder()).isEqualTo(Order.DESC);
    }

    @Test
    void 오픈임박순은_asc_정렬을_사용한다() {
        // given
        // when
        QuerydslShowSortResolver.SortOrder result =
                showSortSupport.resolveSortOrder(ShowSort.SHOW_START_APPROACHING);
        // then
        assertThat(result.key()).isEqualTo(ShowSort.SHOW_START_APPROACHING);
        assertThat(result.direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(showSortSupport.primaryOrderSpecifier(result).getOrder()).isEqualTo(Order.ASC);
        assertThat(showSortSupport.tieBreakerOrder(result).getOrder()).isEqualTo(Order.ASC);
    }

    @Test
    void 최신순은_마감여부_등록일_id_순으로_정렬한다() {
        QuerydslShowSortResolver.SortOrder result =
                showSortSupport.resolveSortOrder(ShowSort.LATEST);

        var orders = showSortSupport.orderSpecifiers(result);

        assertThat(orders).as("마감 여부 -> 등록일 -> id, 세 키를 쓴다").hasSize(3);
        assertThat(orders[0].getOrder()).as("마감되지 않은 공연(0)이 먼저 나와야 한다").isEqualTo(Order.ASC);
        assertThat(orders[1].getOrder()).isEqualTo(Order.DESC);
        assertThat(orders[2].getOrder()).isEqualTo(Order.DESC);
    }

    @Test
    void 최신순이_아닌_정렬은_정렬키가_둘이다() {
        QuerydslShowSortResolver.SortOrder result =
                showSortSupport.resolveSortOrder(ShowSort.POPULAR);

        assertThat(showSortSupport.orderSpecifiers(result)).hasSize(2);
    }

    @Test
    void 최신순_첫_페이지는_현재_시각으로_마감여부를_판정한다() {
        QuerydslShowSortResolver.SortOrder result =
                showSortSupport.resolveSortOrder(ShowSort.LATEST);

        assertThat(result.saleClosedEvaluatedAt()).isEqualTo(NOW);
    }

    @Test
    void 최신순_다음_페이지는_커서에_적힌_판정_시각을_그대로_쓴다() {
        // 페이지를 넘기는 사이 마감된 공연이 그룹을 옮기면 중복·누락이 생긴다. 시각을 고정한다.
        LocalDateTime frozen = NOW.minusHours(3);
        ShowCursor cursor =
                new ShowCursor(
                        ShowSort.LATEST, "DESC", "2026-09-14T09:00", 10L, 0, frozen.toString());

        QuerydslShowSortResolver.SortOrder result =
                showSortSupport.resolveSortOrder(ShowSort.LATEST, cursor);

        assertThat(result.saleClosedEvaluatedAt()).isEqualTo(frozen);
    }

    @Test
    void 최신순_커서에_판정_시각이_없으면_INVALID_INPUT_예외를_던진다() {
        ShowCursor legacyCursor = new ShowCursor(ShowSort.LATEST, "DESC", "2026-09-14T09:00", 10L);

        assertThatThrownBy(() -> showSortSupport.resolveSortOrder(ShowSort.LATEST, legacyCursor))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 최신순_커서의_판정_시각_형식이_틀리면_INVALID_INPUT_예외를_던진다() {
        ShowCursor cursor =
                new ShowCursor(
                        ShowSort.LATEST, "DESC", "2026-09-14T09:00", 10L, 0, "not-a-datetime");

        assertThatThrownBy(() -> showSortSupport.resolveSortOrder(ShowSort.LATEST, cursor))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 인기순_커서는_마감여부_판정_시각을_요구하지_않는다() {
        ShowCursor cursor = new ShowCursor(ShowSort.POPULAR, "DESC", "10", 1L);

        QuerydslShowSortResolver.SortOrder result =
                showSortSupport.resolveSortOrder(ShowSort.POPULAR, cursor);

        assertThat(result.saleClosedEvaluatedAt()).isNull();
    }
}
