package com.ticket.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.api.MemberStatus;
import com.ticket.member.api.RawPassword;
import com.ticket.security.token.AuthTokenIssuer;
import com.ticket.security.token.IssuedAuthTokens;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class LoginUseCaseTest {
    @Mock
    private MemberAccountApi memberAccountOperations;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @InjectMocks
    private LoginUseCase useCase;

    @Test
    void successful_login_issues_tokens() {
        MemberStatus member = new MemberStatus(1L, true, "MEMBER");
        IssuedAuthTokens response =
                new IssuedAuthTokens("access-token-value", "refresh-token-value", "Bearer", 1800L, 1209600L, 1L);

        when(memberAccountOperations.authenticate("user@example.com", RawPassword.create("password")))
                .thenReturn(member);
        when(authTokenIssuer.issueTokens(1L, "MEMBER")).thenReturn(response);

        LoginUseCase.Result result = useCase.execute(new LoginUseCase.Input("user@example.com", "password"));
        LoginUseCase.Output output = result.output();

        assertThat(output.accessToken()).isEqualTo(response.accessToken());
        assertThat(output.tokenType()).isEqualTo(response.tokenType());
        assertThat(output.expiresIn()).isEqualTo(response.expiresIn());
        assertThat(output.memberId()).isEqualTo(response.memberId());
        assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(result.toString()).doesNotContain("access-token-value").doesNotContain("refresh-token-value");
        verify(memberAccountOperations).authenticate("user@example.com", RawPassword.create("password"));
        verify(authTokenIssuer).issueTokens(1L, "MEMBER");
    }
}
