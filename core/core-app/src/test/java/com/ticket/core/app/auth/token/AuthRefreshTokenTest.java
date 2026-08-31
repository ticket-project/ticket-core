package com.ticket.core.domain.auth.token;

import com.ticket.support.error.CoreException;
import com.ticket.core.domain.error.DomainErrorType;
import com.ticket.core.domain.auth.token.AuthRefreshToken;
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
                .isInstanceOf(CoreException.class)
                .satisfies(error -> assertThat(((CoreException) error).getErrorType()).isEqualTo(DomainErrorType.AUTHENTICATION_FAILED));
    }
}
