package com.ticket.catalog.internal.domain.showlike.model;

import com.ticket.catalog.internal.domain.show.Show;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowLikeTest {

    @Test
    void 회원_id와_공연이_있으면_좋아요를_생성한다() {
        //given
        Show show = org.mockito.Mockito.mock(Show.class);

        //when
        ShowLike showLike = new ShowLike(1L, show);

        //then
        assertThat(showLike.getMemberId()).isEqualTo(1L);
        assertThat(showLike.getShow()).isSameAs(show);
    }

    @Test
    void 회원_id가_없으면_예외를_던진다() {
        //given
        Show show = org.mockito.Mockito.mock(Show.class);

        //when
        //then
        assertThatThrownBy(() -> new ShowLike(null, show))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("memberId");
    }

    @Test
    void 공연이_없으면_예외를_던진다() {
        //when
        //then
        assertThatThrownBy(() -> new ShowLike(1L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("show");
    }
}
