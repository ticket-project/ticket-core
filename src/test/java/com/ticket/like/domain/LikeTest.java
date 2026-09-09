package com.ticket.like.domain;

import com.ticket.like.LikeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class LikeTest {

    @Test
    void 회원_id와_대상_id가_있으면_찜을_생성한다() {
        //given
        //when
        Like like = new Like(1L, LikeType.SHOW, 2L);

        //then
        assertThat(like.getMemberId()).isEqualTo(1L);
        assertThat(like.getLikeType()).isEqualTo(LikeType.SHOW);
        assertThat(like.getTargetId()).isEqualTo(2L);
    }

    @Test
    void 회원_id가_없으면_예외를_던진다() {
        //when
        //then
        assertThatThrownBy(() -> new Like(null, LikeType.SHOW, 2L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("memberId");
    }

    @Test
    void 찜_대상_종류가_없으면_예외를_던진다() {
        //when
        //then
        assertThatThrownBy(() -> new Like(1L, null, 2L))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("likeType");
    }

    @Test
    void 대상_id가_없으면_예외를_던진다() {
        //when
        //then
        assertThatThrownBy(() -> new Like(1L, LikeType.SHOW, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("targetId");
    }
}
