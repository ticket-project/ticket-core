package com.ticket.core.domain.member.vo;

import com.ticket.core.domain.member.model.RawPassword;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class RawPasswordTest {

    @Test
    void 입력한_원문_비밀번호를_그대로_보관한다() {
        //given
        //when
        RawPassword rawPassword = RawPassword.create("password123!");

        //then
        assertThat(rawPassword.getPassword()).isEqualTo("password123!");
    }

    @Test
    void null도_그대로_보관한다() {
        //given
        //when
        RawPassword rawPassword = RawPassword.create(null);

        //then
        assertThat(rawPassword.getPassword()).isNull();
    }

    @Test
    void 같은_비밀번호값이면_동등하다() {
        //given
        //when
        RawPassword first = RawPassword.create("password123!");
        RawPassword second = RawPassword.create("password123!");

        //then
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
