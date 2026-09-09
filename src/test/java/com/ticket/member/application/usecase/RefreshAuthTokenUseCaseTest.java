package com.ticket.member.application.usecase;

import com.ticket.member.domain.Role;
import com.ticket.member.application.AuthRefreshToken;
import com.ticket.member.application.AuthTokenIssuer;
import com.ticket.member.application.IssuedAuthTokens;
import com.ticket.member.application.RefreshTokenStore;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.UnauthenticatedException;
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
    private MemberRepository memberRepository;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @InjectMocks
    private RefreshAuthTokenUseCase useCase;

    @Test
    void valid_refresh_token_rotates_tokens() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getRole()).thenReturn(Role.MEMBER);
        IssuedAuthTokens response = new IssuedAuthTokens("access-token-value", "new-refresh-token-value", "Bearer", 1800L, 1209600L, 3L);

        AuthRefreshToken refreshToken = AuthRefreshToken.from("refresh-token");
        when(refreshTokenStore.validate(refreshToken)).thenReturn(Optional.of(3L));
        when(memberRepository.findActiveById(3L)).thenReturn(Optional.of(member));
        when(authTokenIssuer.rotateTokens(1L, "MEMBER", refreshToken)).thenReturn(response);

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
        verify(memberRepository).findActiveById(3L);
        verify(authTokenIssuer).rotateTokens(1L, "MEMBER", refreshToken);
    }

    @Test
    void invalid_refresh_token_throws_auth_exception() {
        AuthRefreshToken refreshToken = AuthRefreshToken.from("refresh-token");
        when(refreshTokenStore.validate(refreshToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RefreshAuthTokenUseCase.Input(refreshToken)))
                .isInstanceOf(UnauthenticatedException.class);
    }
}
