package com.ticket.core.config.security;

import com.ticket.core.app.auth.token.AuthRefreshToken;
import com.ticket.core.app.auth.token.IssuedAuthTokens;
import com.ticket.core.app.auth.token.RefreshTokenStore;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.model.Email;
import com.ticket.core.domain.member.model.EncodedPassword;
import com.ticket.core.domain.member.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
class JwtAuthTokenManagerTest {

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private JwtAuthTokenManager jwtAuthTokenManager;

    @Test
    void issue_tokens_returns_access_and_refresh_tokens() {
        Member member = createMember(7L);
        when(jwtTokenService.createAccessToken(any(), any())).thenReturn("access-token");
        when(jwtTokenService.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(1209600L);
        when(refreshTokenStore.createRefreshToken(7L, 1209600L)).thenReturn("refresh-token");

        IssuedAuthTokens result = jwtAuthTokenManager.issueTokens(member);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(1800L);
        assertThat(result.memberId()).isEqualTo(7L);
    }

    @Test
    void rotate_tokens_returns_new_access_and_refresh_tokens() {
        Member member = createMember(7L);
        AuthRefreshToken refreshToken = AuthRefreshToken.from("old-refresh");
        when(refreshTokenStore.rotate(refreshToken, 7L, 1209600L)).thenReturn("new-refresh");
        when(jwtProperties.getRefreshTokenExpirationSeconds()).thenReturn(1209600L);
        when(jwtTokenService.createAccessToken(any(), any())).thenReturn("new-access");
        when(jwtTokenService.getAccessTokenExpirationSeconds()).thenReturn(1800L);

        IssuedAuthTokens result = jwtAuthTokenManager.rotateTokens(member, refreshToken);

        verify(refreshTokenStore).rotate(refreshToken, 7L, 1209600L);
        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.memberId()).isEqualTo(7L);
    }

    private Member createMember(final Long id) {
        Member member = new Member(Email.create("user@example.com"), EncodedPassword.create("encoded"), "user", Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
