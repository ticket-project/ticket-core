package com.ticket.core.domain.member.vo;

import com.ticket.core.domain.member.model.Email;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class EmailTest {

    @Test
    void null이면_빈문자열로_정규화한다() {
        //given
        //when
        Email email = Email.create(null);

        //then
        assertThat(email.getEmail()).isEmpty();
    }

    @Test
    void 앞뒤_공백을_제거한다() {
        //given
        //when
        Email email = Email.create("  user@example.com  ");

        //then
        assertThat(email.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void 같은_이메일값이면_동등하다() {
        //given
        //when
        Email first = Email.create("user@example.com");
        Email second = Email.create("user@example.com");

        //then
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
