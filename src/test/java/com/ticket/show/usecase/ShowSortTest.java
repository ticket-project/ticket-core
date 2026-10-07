package com.ticket.show.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ticket.show.exception.ShowErrorCode;
import com.ticket.show.exception.ShowException;

@SuppressWarnings("NonAsciiCharacters")
class ShowSortTest {
    @Test
    void 기본값이면_POPULAR를_사용한다() {
        ShowSort showSort = ShowSort.from(null);

        assertThat(showSort).isEqualTo(ShowSort.POPULAR);
    }

    @Test
    void 공백이면_POPULAR를_사용한다() {
        assertThat(ShowSort.from("  ")).isEqualTo(ShowSort.POPULAR);
    }

    @Test
    void 지원하지_않는_sort면_원문을_담은_예외를_던진다() {
        assertThatThrownBy(() -> ShowSort.from("unknown"))
                .isInstanceOf(ShowException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShowErrorCode.E7002)
                .hasFieldOrPropertyWithValue("data", "지원하지 않는 sort: unknown");
    }

    @Test
    void 대소문자를_정규화하지_않고_원문_그대로_담는다() {
        assertThatThrownBy(() -> ShowSort.from("UNKNOWN_SORT"))
                .isInstanceOf(ShowException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShowErrorCode.E7002)
                .hasFieldOrPropertyWithValue("data", "지원하지 않는 sort: UNKNOWN_SORT");
    }

    @Test
    void 허용한_정렬이면_그대로_돌려준다() {
        assertThat(ShowSort.LATEST.requireOneOf(Set.of(ShowSort.POPULAR, ShowSort.LATEST)))
                .isEqualTo(ShowSort.LATEST);
    }

    @Test
    void 허용하지_않은_정렬이면_apiValue를_담은_예외를_던진다() {
        assertThatThrownBy(() -> ShowSort.SALE_START_APPROACHING.requireOneOf(Set.of(ShowSort.POPULAR)))
                .isInstanceOf(ShowException.class)
                .hasFieldOrPropertyWithValue("errorCode", ShowErrorCode.E7002)
                .hasFieldOrPropertyWithValue("data", "지원하지 않는 sort: saleStartApproaching");
    }
}
