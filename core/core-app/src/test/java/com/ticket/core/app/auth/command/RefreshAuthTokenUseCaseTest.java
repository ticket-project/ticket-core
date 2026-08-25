package com.ticket.core.app.auth.command;

import com.ticket.core.domain.auth.token.AuthRefreshToken;
import com.ticket.core.domain.auth.token.AuthTokenManager;
import com.ticket.core.domain.auth.token.IssuedAuthTokens;
import com.ticket.core.domain.auth.token.RefreshTokenStore;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.query.MemberFinder;
import com.ticket.support.error.AuthException;
import com.ticket.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class RefreshAuthTokenUseCaseTest {

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private MemberFinder memberFinder;

    @Mock
    private AuthTokenManager authTokenManager;

    @InjectMocks
    private RefreshAuthTokenUseCase useCase;

    @Test
    void valid_refresh_token_rotates_tokens() {
        Member member = mock(Member.class);
        IssuedAuthTokens response = new IssuedAuthTokens("access-token-value", "new-refresh-token-value", "Bearer", 1800L, 3L);

        AuthRefreshToken refreshToken = AuthRefreshToken.from("refresh-token");
        when(refreshTokenStore.validate(refreshToken)).thenReturn(Optional.of(3L));
        when(memberFinder.findActiveMemberById(3L)).thenReturn(member);
        when(authTokenManager.rotateTokens(member, refreshToken)).thenReturn(response);

        RefreshAuthTokenUseCase.Result result =
                useCase.execute(new RefreshAuthTokenUseCase.Input(refreshToken));
        RefreshAuthTokenUseCase.Output output = result.output();

        assertThat(output.accessToken()).isEqualTo(response.accessToken());
        assertThat(output.tokenType()).isEqualTo(response.tokenType());
        assertThat(output.expiresIn()).isEqualTo(response.expiresIn());
        assertThat(output.memberId()).isEqualTo(response.memberId());
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token-value");
        assertThat(result.toString())
                .doesNotContain("access-token-value")
                .doesNotContain("new-refresh-token-value");
        verify(refreshTokenStore).validate(refreshToken);
        verify(memberFinder).findActiveMemberById(3L);
        verify(authTokenManager).rotateTokens(member, refreshToken);
    }

    @Test
    void invalid_refresh_token_throws_auth_exception() {
        AuthRefreshToken refreshToken = AuthRefreshToken.from("refresh-token");
        when(refreshTokenStore.validate(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RefreshAuthTokenUseCase.Input(refreshToken)))
                .isInstanceOf(AuthException.class)
                .satisfies(exception -> assertThat(((AuthException) exception).getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_ERROR));
    }
}
