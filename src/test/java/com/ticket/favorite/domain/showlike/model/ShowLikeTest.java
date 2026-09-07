package com.ticket.favorite.domain.showlike.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowLikeTest {

    @Test
    void 회원_id와_공연_id가_있으면_좋아요를_생성한다() {
        //given
        //when
        ShowLike showLike = new ShowLike(1L, 2L);

        //then
        assertThat(showLike.getMemberId()).isEqualTo(1L);
        assertThat(showLike.getShowId()).isEqualTo(2L);
    }

    @Test
    void 회원_id가_없으면_예외를_던진다() {
        //when
        //then
        assertThatThrownBy(() -> new ShowLike(null, 2L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("memberId");
    }

    @Test
    void 공연_id가_없으면_예외를_던진다() {
        //when
        //then
        assertThatThrownBy(() -> new ShowLike(1L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("showId");
    }
}
