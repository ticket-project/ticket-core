package com.ticket.identity.internal.application.auth.command;

import com.ticket.identity.internal.domain.member.model.Role;
import com.ticket.identity.internal.application.auth.CredentialAuthenticator;
import com.ticket.identity.internal.application.auth.token.AuthTokenIssuer;
import com.ticket.identity.internal.application.auth.token.IssuedAuthTokens;
import com.ticket.identity.internal.domain.member.model.Member;
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
    private CredentialAuthenticator credentialAuthenticator;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @InjectMocks
    private LoginUseCase useCase;

    @Test
    void successful_login_issues_tokens() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getRole()).thenReturn(Role.MEMBER);
        IssuedAuthTokens response = new IssuedAuthTokens("access-token-value", "refresh-token-value", "Bearer", 1800L, 1209600L, 1L);

        when(credentialAuthenticator.authenticate("user@example.com", "password")).thenReturn(member);
        when(authTokenIssuer.issueTokens(1L, "MEMBER")).thenReturn(response);

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
        verify(credentialAuthenticator).authenticate("user@example.com", "password");
        verify(authTokenIssuer).issueTokens(1L, "MEMBER");
    }
}
