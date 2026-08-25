package com.ticket.core.app.auth.command;

import com.ticket.core.app.auth.AuthService;
import com.ticket.core.domain.auth.token.AuthTokenManager;
import com.ticket.core.domain.auth.token.IssuedAuthTokens;
import com.ticket.core.domain.member.model.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class LoginUseCaseTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthTokenManager authTokenManager;

    @InjectMocks
    private LoginUseCase useCase;

    @Test
    void successful_login_issues_tokens() {
        Member member = mock(Member.class);
        IssuedAuthTokens response = new IssuedAuthTokens("access-token-value", "refresh-token-value", "Bearer", 1800L, 1L);

        when(authService.login("user@example.com", "password")).thenReturn(member);
        when(authTokenManager.issueTokens(member)).thenReturn(response);

        LoginUseCase.Result result = useCase.execute(new LoginUseCase.Input("user@example.com", "password"));
        LoginUseCase.Output output = result.output();

        assertThat(output.accessToken()).isEqualTo(response.accessToken());
        assertThat(output.tokenType()).isEqualTo(response.tokenType());
        assertThat(output.expiresIn()).isEqualTo(response.expiresIn());
        assertThat(output.memberId()).isEqualTo(response.memberId());
        assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(result.toString())
                .doesNotContain("access-token-value")
                .doesNotContain("refresh-token-value");
        verify(authService).login("user@example.com", "password");
        verify(authTokenManager).issueTokens(member);
    }
}
