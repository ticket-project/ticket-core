package com.ticket.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class AccessTokenAuthenticatorServiceTest {
    @Mock
    private AccessTokenReader accessTokenReader;

    @Mock
    private MemberAccountApi memberAccountApi;

    @InjectMocks
    private AccessTokenAuthenticatorService service;

    @Test
    void 유효한_토큰이면_인증된_회원을_반환한다() {
        AuthenticatedMember member = new AuthenticatedMember(1L, "MEMBER");
        when(accessTokenReader.read("valid-token")).thenReturn(AccessTokenReadResult.authenticated(member));

        AuthenticatedMember result = service.authenticate("valid-token");

        assertThat(result).isEqualTo(member);
        verify(memberAccountApi).getActiveIdentity(1L);
    }

    @Test
    void 만료된_토큰이면_인증_예외를_던진다() {
        when(accessTokenReader.read("expired-token")).thenReturn(AccessTokenReadResult.expired());

        assertThatThrownBy(() -> service.authenticate("expired-token")).isInstanceOf(UnauthenticatedException.class);
        verifyNoInteractions(memberAccountApi);
    }

    @Test
    void 무효한_토큰이면_인증_예외를_던진다() {
        when(accessTokenReader.read("invalid-token")).thenReturn(AccessTokenReadResult.invalid());

        assertThatThrownBy(() -> service.authenticate("invalid-token")).isInstanceOf(UnauthenticatedException.class);
        verifyNoInteractions(memberAccountApi);
    }

    @Test
    void 탈퇴한_회원의_유효한_토큰은_인증하지_않는다() {
        when(accessTokenReader.read("withdrawn-token"))
                .thenReturn(AccessTokenReadResult.authenticated(new AuthenticatedMember(7L, "MEMBER")));
        when(memberAccountApi.getActiveIdentity(7L)).thenThrow(new NotFoundException());

        assertThat(service.read("withdrawn-token")).isInstanceOf(AccessTokenReadResult.Invalid.class);
        assertThatThrownBy(() -> service.authenticate("withdrawn-token")).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void 회원_DB_오류를_유효하지_않은_토큰으로_숨기지_않는다() {
        when(accessTokenReader.read("valid-token"))
                .thenReturn(AccessTokenReadResult.authenticated(new AuthenticatedMember(7L, "MEMBER")));
        when(memberAccountApi.getActiveIdentity(7L)).thenThrow(new IllegalStateException("DB unavailable"));

        assertThatThrownBy(() -> service.read("valid-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB unavailable");
    }
}
