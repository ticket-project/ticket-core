package com.ticket.core.domain.show.meta;

import com.ticket.support.error.CoreException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowSortKeyTest {

    @Test
    void sort값이_null이면_기본정렬로_POPULAR를_반환한다() {
        //given
        //when
        //then
        assertThat(ShowSortKey.fromApiValue(null)).isEqualTo(ShowSortKey.POPULAR);
    }

    @Test
    void sort값이_blank면_기본정렬로_POPULAR를_반환한다() {
        //given
        //when
        //then
        assertThat(ShowSortKey.fromApiValue("   ")).isEqualTo(ShowSortKey.POPULAR);
    }

    @Test
    void sort값은_대소문자와_무관하게_매칭한다() {
        //given
        //when
        //then
        assertThat(ShowSortKey.fromApiValue("LaTeSt")).isEqualTo(ShowSortKey.LATEST);
    }

    @Test
    void 지원하지_않는_sort값이면_NOT_SUPPORT_SHOW_SORT_예외를_던진다() {
        //given
        //when
        //then
        assertThatThrownBy(() -> ShowSortKey.fromApiValue("unknown"))
                .isInstanceOf(CoreException.class)
                .satisfies(thrown -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_SUPPORT_SHOW_SORT));
    }
}

