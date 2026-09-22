package com.ticket.member.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class RawPasswordTest {
    @Test
    void 입력한_원문_비밀번호를_그대로_보관한다() {
        RawPassword rawPassword = RawPassword.create("password123!");
        assertThat(rawPassword.getPassword()).isEqualTo("password123!");
    }

    @Test
    void null도_그대로_보관한다() {
        RawPassword rawPassword = RawPassword.create(null);
        assertThat(rawPassword.getPassword()).isNull();
    }

    @Test
    void 같은_비밀번호값이면_동등하다() {
        RawPassword first = RawPassword.create("password123!");
        RawPassword second = RawPassword.create("password123!");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
