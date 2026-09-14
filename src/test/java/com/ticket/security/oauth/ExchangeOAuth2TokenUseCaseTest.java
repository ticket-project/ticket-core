package com.ticket.security.oauth;

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
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.security.token.AuthTokenIssuer;
import com.ticket.security.token.IssuedAuthTokens;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class ExchangeOAuth2TokenUseCaseTest {
    @Mock private OAuth2AuthCodeStore oauth2AuthCodeStore;
    @Mock private MemberAccountApi memberAccountOperations;
    @Mock private AuthTokenIssuer authTokenIssuer;
    @InjectMocks private ExchangeOAuth2TokenUseCase useCase;

    @Test
    void valid_code_issues_tokens() {
        MemberStatus member = new MemberStatus(1L, true, "MEMBER");
        IssuedAuthTokens response =
                new IssuedAuthTokens(
                        "access-token-value", "refresh-token-value", "Bearer", 1800L, 1209600L, 7L);

        when(oauth2AuthCodeStore.consumeCode("oauth-code")).thenReturn(Optional.of(7L));
        when(memberAccountOperations.requireActiveIdentity(7L)).thenReturn(member);
        when(authTokenIssuer.issueTokens(1L, "MEMBER")).thenReturn(response);

        ExchangeOAuth2TokenUseCase.Result result =
                useCase.execute(new ExchangeOAuth2TokenUseCase.Input("oauth-code"));
        ExchangeOAuth2TokenUseCase.Output output = result.output();

        assertThat(output.accessToken()).isEqualTo(response.accessToken());
        assertThat(output.tokenType()).isEqualTo(response.tokenType());
        assertThat(output.expiresIn()).isEqualTo(response.expiresIn());
        assertThat(output.memberId()).isEqualTo(response.memberId());
        assertThat(result.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(result.toString())
                .doesNotContain("access-token-value")
                .doesNotContain("refresh-token-value");
        verify(oauth2AuthCodeStore).consumeCode("oauth-code");
        verify(memberAccountOperations).requireActiveIdentity(7L);
        verify(authTokenIssuer).issueTokens(1L, "MEMBER");
    }

    @Test
    void invalid_code_throws_auth_exception() {
        when(oauth2AuthCodeStore.consumeCode("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ExchangeOAuth2TokenUseCase.Input("invalid")))
                .isInstanceOf(UnauthenticatedException.class);
    }
}
