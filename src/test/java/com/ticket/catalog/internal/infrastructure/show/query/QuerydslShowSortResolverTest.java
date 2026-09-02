package com.ticket.catalog.internal.infrastructure.show.query;

import com.querydsl.core.types.Order;
import com.ticket.catalog.internal.application.show.query.ShowSort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class QuerydslShowSortResolverTest {

    private final QuerydslShowSortResolver showSortSupport = new QuerydslShowSortResolver();

    @Test
    void 인기순은_desc_정렬을_사용한다() {
        //given
        //when
        QuerydslShowSortResolver.SortOrder result = showSortSupport.resolveSortOrder(ShowSort.POPULAR);

        //then
        assertThat(result.key()).isEqualTo(ShowSort.POPULAR);
        assertThat(result.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(showSortSupport.primaryOrderSpecifier(result).getOrder()).isEqualTo(Order.DESC);
        assertThat(showSortSupport.tieBreakerOrder(result).getOrder()).isEqualTo(Order.DESC);
    }

    @Test
    void 오픈임박순은_asc_정렬을_사용한다() {
        //given
        //when
        QuerydslShowSortResolver.SortOrder result = showSortSupport.resolveSortOrder(ShowSort.SHOW_START_APPROACHING);

        //then
        assertThat(result.key()).isEqualTo(ShowSort.SHOW_START_APPROACHING);
        assertThat(result.direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(showSortSupport.primaryOrderSpecifier(result).getOrder()).isEqualTo(Order.ASC);
        assertThat(showSortSupport.tieBreakerOrder(result).getOrder()).isEqualTo(Order.ASC);
    }
}
