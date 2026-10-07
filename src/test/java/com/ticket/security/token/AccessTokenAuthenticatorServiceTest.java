package com.ticket.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.exception.UnauthenticatedException;
import com.ticket.security.jwt.JwtAccessTokenCodec;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
class AccessTokenAuthenticatorServiceTest {
    @Mock
    private JwtAccessTokenCodec accessTokenReader;

    @InjectMocks
    private AccessTokenAuthenticatorService service;

    @Test
    void 유효한_토큰이면_인증된_회원을_반환한다() {
        AuthenticatedMember member = new AuthenticatedMember(1L, "MEMBER");
        when(accessTokenReader.read("valid-token")).thenReturn(new AccessTokenReadResult.Authenticated(member));

        AuthenticatedMember result = service.authenticate("valid-token");

        assertThat(result).isEqualTo(member);
    }

    @Test
    void 만료된_토큰이면_인증_예외를_던진다() {
        when(accessTokenReader.read("expired-token")).thenReturn(new AccessTokenReadResult.Expired());

        assertThatThrownBy(() -> service.authenticate("expired-token")).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void 무효한_토큰이면_인증_예외를_던진다() {
        when(accessTokenReader.read("invalid-token")).thenReturn(new AccessTokenReadResult.Invalid());

        assertThatThrownBy(() -> service.authenticate("invalid-token")).isInstanceOf(UnauthenticatedException.class);
    }
}
