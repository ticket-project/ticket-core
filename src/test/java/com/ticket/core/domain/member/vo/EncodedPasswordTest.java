package com.ticket.core.domain.member.vo;

import com.ticket.core.domain.member.model.EncodedPassword;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NonAsciiCharacters")
class EncodedPasswordTest {

    @Test
    void 인코딩된_비밀번호를_그대로_보관한다() {
        //given
        //when
        EncodedPassword encodedPassword = EncodedPassword.create("encoded-value");

        //then
        assertThat(encodedPassword.getPassword()).isEqualTo("encoded-value");
    }

    @Test
    void 같은_인코딩값이면_동등하다() {
        //given
        //when
        EncodedPassword first = EncodedPassword.create("encoded-value");
        EncodedPassword second = EncodedPassword.create("encoded-value");

        //then
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
