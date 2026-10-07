package com.ticket.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ticket.security.exception.AuthException;
import com.ticket.security.exception.SecurityErrorCode;

@SuppressWarnings("NonAsciiCharacters")
class AuthRefreshTokenTest {
    @Test
    void 앞뒤_공백을_제거한다() {
        AuthRefreshToken token = AuthRefreshToken.from("  refresh-token  ");

        assertThat(token.value()).isEqualTo("refresh-token");
    }

    @Test
    void 빈값이면_인증_예외를_던진다() {
        assertThatThrownBy(() -> AuthRefreshToken.from("   "))
                .isInstanceOf(AuthException.class)
                .hasFieldOrPropertyWithValue("errorCode", SecurityErrorCode.E1000);
    }

    @Test
    void 문자열로_바꿔도_토큰_값을_드러내지_않는다() {
        assertThat(AuthRefreshToken.from("refresh-token").toString()).doesNotContain("refresh-token");
    }
}
