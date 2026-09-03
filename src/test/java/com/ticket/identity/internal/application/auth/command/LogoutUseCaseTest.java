package com.ticket.identity.internal.application.auth.command;

import com.ticket.identity.internal.application.auth.token.AuthRefreshToken;
import com.ticket.identity.internal.application.auth.token.RefreshTokenStore;
import com.ticket.identity.internal.exception.AuthorizationException;
import com.ticket.identity.internal.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class LogoutUseCaseTest {

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private LogoutUseCase useCase;

    @Test
    void owned_token_is_revoked() {
        AuthRefreshToken refreshToken = AuthRefreshToken.from("refresh-token");
        when(refreshTokenStore.revokeIfOwned(refreshToken, 1L)).thenReturn(true);

        useCase.execute(new LogoutUseCase.Input(1L, refreshToken));

        verify(refreshTokenStore).revokeIfOwned(refreshToken, 1L);
    }

    @Test
    void foreign_token_throws_authorization_exception() {
        AuthRefreshToken refreshToken = AuthRefreshToken.from("refresh-token");
        when(refreshTokenStore.revokeIfOwned(refreshToken, 1L)).thenReturn(false);
        when(refreshTokenStore.validateWithoutConsume(refreshToken)).thenReturn(Optional.of(2L));

        assertThatThrownBy(() -> useCase.execute(new LogoutUseCase.Input(1L, refreshToken)))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    void missing_owner_check_result_throws_authentication_exception() {
        AuthRefreshToken refreshToken = AuthRefreshToken.from("refresh-token");
        when(refreshTokenStore.revokeIfOwned(refreshToken, 1L)).thenReturn(false);
        when(refreshTokenStore.validateWithoutConsume(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new LogoutUseCase.Input(1L, refreshToken)))
                .isInstanceOf(UnauthenticatedException.class);
    }
}
