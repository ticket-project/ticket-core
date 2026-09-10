package com.ticket.show.catalog.application;

import com.ticket.show.catalog.exception.UnsupportedShowSortException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowSortTest {

    @Test
    void 기본값이면_POPULAR를_사용한다() {
        ShowSort showSort = ShowSort.from(null);

        assertThat(showSort).isEqualTo(ShowSort.POPULAR);
        assertThat(showSort.apiValue()).isEqualTo("popular");
    }

    @Test
    void 지원하지_않는_sort면_예외를_던진다() {
        assertThatThrownBy(() -> ShowSort.from("unknown"))
                .isInstanceOf(UnsupportedShowSortException.class);
    }
}
