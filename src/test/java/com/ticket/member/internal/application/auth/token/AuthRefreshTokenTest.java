package com.ticket.member.internal.application.auth.token;

import com.ticket.member.internal.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .isInstanceOf(UnauthenticatedException.class);
    }
}
