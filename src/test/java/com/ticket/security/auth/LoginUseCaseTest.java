package com.ticket.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.security.exception.UnauthenticatedException;
import com.ticket.security.token.AuthTokenIssuer;
import com.ticket.security.token.IssuedAuthTokens;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class LoginUseCaseTest {
    @Mock
    private MemberAccountApi memberAccountApi;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @InjectMocks
    private LoginUseCase useCase;

    @Test
    void successful_login_issues_tokens() {
        MemberStatus member = new MemberStatus(1L, true, "MEMBER");
        IssuedAuthTokens response =
                new IssuedAuthTokens("access-token-value", "refresh-token-value", "Bearer", 1800L, 1209600L, 1L);

        when(memberAccountApi.authenticate("user@example.com", RawPassword.create("password")))
                .thenReturn(Optional.of(member));
        when(authTokenIssuer.issueTokens(1L, "MEMBER")).thenReturn(response);

        LoginUseCase.Result result = useCase.execute(new LoginUseCase.Input("user@example.com", "password"));
        LoginUseCase.Output output = result.output();

        assertThat(output.accessToken()).isEqualTo(response.accessToken());
        assertThat(output.tokenType()).isEqualTo(response.tokenType());
        assertThat(output.expiresIn()).isEqualTo(response.expiresIn());
        assertThat(output.memberId()).isEqualTo(response.memberId());
        assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(result.toString()).doesNotContain("access-token-value").doesNotContain("refresh-token-value");
        verify(memberAccountApi).authenticate("user@example.com", RawPassword.create("password"));
        verify(authTokenIssuer).issueTokens(1L, "MEMBER");
    }

    @Test
    void credential_mismatch_is_authentication_failure() {
        when(memberAccountApi.authenticate("user@example.com", RawPassword.create("wrong")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Input("user@example.com", "wrong")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage(UnauthenticatedException.MESSAGE);
    }
}
