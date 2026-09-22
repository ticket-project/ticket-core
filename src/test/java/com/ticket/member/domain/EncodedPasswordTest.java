package com.ticket.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class EncodedPasswordTest {
    @Test
    void 인코딩된_비밀번호를_그대로_보관한다() {
        EncodedPassword encodedPassword = EncodedPassword.create("encoded-value");
        assertThat(encodedPassword.getPassword()).isEqualTo("encoded-value");
    }

    @Test
    void 같은_인코딩값이면_동등하다() {
        EncodedPassword first = EncodedPassword.create("encoded-value");
        EncodedPassword second = EncodedPassword.create("encoded-value");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
