package com.ticket.core.app.show.query;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
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
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType()).isEqualTo(ApplicationErrorType.NOT_SUPPORT_SHOW_SORT));
    }
}
