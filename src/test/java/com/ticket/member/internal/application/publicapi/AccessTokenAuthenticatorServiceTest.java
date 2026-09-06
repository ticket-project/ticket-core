package com.ticket.member.internal.application.publicapi;

import com.ticket.member.AuthenticatedMember;
import com.ticket.member.internal.application.auth.token.AccessTokenReadResult;
import com.ticket.member.internal.application.auth.token.AccessTokenReader;
import com.ticket.member.internal.exception.UnauthenticatedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class AccessTokenAuthenticatorServiceTest {

    @Mock
    private AccessTokenReader accessTokenReader;

    @InjectMocks
    private AccessTokenAuthenticatorService service;

    @Test
    void 유효한_토큰이면_인증된_회원을_반환한다() {
        AuthenticatedMember member = new AuthenticatedMember(1L, "MEMBER");
        when(accessTokenReader.read("valid-token")).thenReturn(AccessTokenReadResult.authenticated(member));

        AuthenticatedMember result = service.authenticate("valid-token");

        assertThat(result).isEqualTo(member);
    }

    @Test
    void 만료된_토큰이면_인증_예외를_던진다() {
        when(accessTokenReader.read("expired-token")).thenReturn(AccessTokenReadResult.expired());

        assertThatThrownBy(() -> service.authenticate("expired-token"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void 무효한_토큰이면_인증_예외를_던진다() {
        when(accessTokenReader.read("invalid-token")).thenReturn(AccessTokenReadResult.invalid());

        assertThatThrownBy(() -> service.authenticate("invalid-token"))
                .isInstanceOf(UnauthenticatedException.class);
    }
}
