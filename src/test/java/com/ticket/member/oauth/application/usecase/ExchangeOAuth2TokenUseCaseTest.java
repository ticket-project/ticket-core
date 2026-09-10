package com.ticket.member.oauth.application.usecase;

import com.ticket.member.account.domain.Role;
import com.ticket.member.oauth.application.OAuth2AuthCodeStore;
import com.ticket.member.auth.application.AuthTokenIssuer;
import com.ticket.member.auth.application.IssuedAuthTokens;
import com.ticket.member.account.domain.Member;
import com.ticket.member.account.domain.MemberRepository;
import com.ticket.member.support.exception.UnauthenticatedException;
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
                .isInstanceOf(UnauthenticatedException.class);
    }
}
