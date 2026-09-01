package com.ticket.core.app.auth.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.core.domain.member.model.Role;
import com.ticket.core.app.auth.oauth2.OAuth2AuthCodeStore;
import com.ticket.core.app.auth.token.AuthTokenIssuer;
import com.ticket.core.app.auth.token.IssuedAuthTokens;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
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
class ExchangeOAuth2TokenUseCaseTest {

    @Mock
    private OAuth2AuthCodeStore oAuth2AuthCodeStore;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuthTokenIssuer authTokenIssuer;

    @InjectMocks
    private ExchangeOAuth2TokenUseCase useCase;

    @Test
    void valid_code_issues_tokens() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(member.getRole()).thenReturn(Role.MEMBER);
        IssuedAuthTokens response = new IssuedAuthTokens("access-token-value", "refresh-token-value", "Bearer", 1800L, 1209600L, 7L);

        when(oAuth2AuthCodeStore.consumeCode("oauth-code")).thenReturn(Optional.of(7L));
        when(memberRepository.findActiveById(7L)).thenReturn(Optional.of(member));
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
        verify(oAuth2AuthCodeStore).consumeCode("oauth-code");
        verify(memberRepository).findActiveById(7L);
        verify(authTokenIssuer).issueTokens(1L, "MEMBER");
    }

    @Test
    void invalid_code_throws_auth_exception() {
        when(oAuth2AuthCodeStore.consumeCode("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new ExchangeOAuth2TokenUseCase.Input("invalid")))
                .isInstanceOf(CoreException.class)
                .satisfies(exception -> assertThat(((CoreException) exception).getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_ERROR));
    }
}
