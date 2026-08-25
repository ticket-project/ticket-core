package com.ticket.core.domain.auth.token;

import com.ticket.core.domain.auth.token.AuthRefreshToken;
import com.ticket.support.error.AuthException;
import com.ticket.support.error.ErrorType;
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
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_ERROR));
    }
}
