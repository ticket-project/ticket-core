package com.ticket.core.infra.auth.token;

import com.ticket.core.app.auth.token.AuthRefreshToken;
import com.ticket.core.app.auth.token.IssuedAuthTokens;
import com.ticket.core.app.auth.token.RefreshTokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class JwtAuthTokenIssuerTest {

    @Mock
    private JwtAccessTokenCodec jwtAccessTokenCodec;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private JwtAuthTokenIssuer jwtAuthTokenIssuer;

    @Test
    void issue_tokens_returns_access_and_refresh_tokens() {
        when(jwtAccessTokenCodec.createAccessToken(any(), any())).thenReturn("access-token");
        when(jwtAccessTokenCodec.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(1209600L);
        when(refreshTokenStore.createRefreshToken(7L, 1209600L)).thenReturn("refresh-token");

        IssuedAuthTokens result = jwtAuthTokenIssuer.issueTokens(7L, "MEMBER");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(1800L);
        assertThat(result.memberId()).isEqualTo(7L);
    }

    @Test
    void rotate_tokens_returns_new_access_and_refresh_tokens() {
        AuthRefreshToken refreshToken = AuthRefreshToken.from("old-refresh");
        when(refreshTokenStore.rotate(refreshToken, 7L, 1209600L)).thenReturn("new-refresh");
        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(1209600L);
        when(jwtAccessTokenCodec.createAccessToken(any(), any())).thenReturn("new-access");
        when(jwtAccessTokenCodec.getAccessTokenExpirationSeconds()).thenReturn(1800L);

        IssuedAuthTokens result = jwtAuthTokenIssuer.rotateTokens(7L, "MEMBER", refreshToken);

        verify(refreshTokenStore).rotate(refreshToken, 7L, 1209600L);
        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.memberId()).isEqualTo(7L);
    }

}
