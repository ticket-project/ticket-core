package com.ticket.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class EmailTest {
    @Test
    void null이면_빈문자열로_정규화한다() {
        Email email = Email.create(null);
        assertThat(email.getEmail()).isEmpty();
    }

    @Test
    void 앞뒤_공백을_제거한다() {
        Email email = Email.create("  user@example.com  ");
        assertThat(email.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void 같은_이메일값이면_동등하다() {
        Email first = Email.create("user@example.com");
        Email second = Email.create("user@example.com");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
