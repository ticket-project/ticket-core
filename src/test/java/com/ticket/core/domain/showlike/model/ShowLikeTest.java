package com.ticket.core.domain.showlike.model;

import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.show.model.Show;
import com.ticket.core.domain.member.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("NonAsciiCharacters")
class ShowLikeTest {

    @Test
    void 회원과_공연이_있으면_좋아요를_생성한다() {
        //given
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "사용자", Role.MEMBER);
        Show show = org.mockito.Mockito.mock(Show.class);

        //when
        ShowLike showLike = new ShowLike(member, show);

        //then
        assertThat(showLike.getMember()).isSameAs(member);
        assertThat(showLike.getShow()).isSameAs(show);
    }

    @Test
    void 회원이_없으면_예외를_던진다() {
        //given
        Show show = org.mockito.Mockito.mock(Show.class);

        //when
        //then
        assertThatThrownBy(() -> new ShowLike(null, show))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("member");
    }

    @Test
    void 공연이_없으면_예외를_던진다() {
        //given
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "사용자", Role.MEMBER);

        //when
        //then
        assertThatThrownBy(() -> new ShowLike(member, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("show");
    }
}
