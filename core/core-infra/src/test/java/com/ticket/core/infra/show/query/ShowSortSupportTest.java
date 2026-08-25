package com.ticket.core.infra.show.query;

import com.querydsl.core.types.Order;
import com.ticket.core.domain.show.meta.ShowSortKey;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class ShowSortSupportTest {

    private final ShowSortSupport showSortSupport = new ShowSortSupport();

    @Test
    void 인기순은_desc_정렬을_사용한다() {
        //given
        //when
        ShowSortSupport.SortOrder result = showSortSupport.resolveSortOrder("popular");

        //then
        assertThat(result.key()).isEqualTo(ShowSortKey.POPULAR);
        assertThat(result.direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(showSortSupport.primaryOrderSpecifier(result).getOrder()).isEqualTo(Order.DESC);
        assertThat(showSortSupport.tieBreakerOrder(result).getOrder()).isEqualTo(Order.DESC);
    }

    @Test
    void 오픈임박순은_asc_정렬을_사용한다() {
        //given
        //when
        ShowSortSupport.SortOrder result = showSortSupport.resolveSortOrder("showStartApproaching");

        //then
        assertThat(result.key()).isEqualTo(ShowSortKey.SHOW_START_APPROACHING);
        assertThat(result.direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(showSortSupport.primaryOrderSpecifier(result).getOrder()).isEqualTo(Order.ASC);
        assertThat(showSortSupport.tieBreakerOrder(result).getOrder()).isEqualTo(Order.ASC);
    }
}

