package com.ticket.security.token;

import org.springframework.stereotype.Service;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.security.api.AccessTokenAuthenticator;

import lombok.RequiredArgsConstructor;

/**
 * {@link AccessTokenAuthenticator}의 security 소유 구현이다.
 *
 * <p>만료와 무효를 구분하지 않고 같은 인증 실패로 다룬다. 구분이 필요한 것은 HTTP 경로뿐이고(401 응답의 안내 문구가 갈린다), 그쪽은 {@link AccessTokenReader}의 3-way 결과를
 * 직접 읽는다. 이 계약은 구분이 필요 없는 호출부(WebSocket STOMP CONNECT 등)를 위한 것이라 실패를 하나로 합쳐 내보낸다.
 */
@Service
@RequiredArgsConstructor
public class AccessTokenAuthenticatorService implements AccessTokenAuthenticator {
    private final AccessTokenReader accessTokenReader;

    @Override
    public AuthenticatedMember authenticate(final String accessToken) {
        if (accessTokenReader.read(accessToken) instanceof AccessTokenReadResult.Authenticated authenticated) {
            return authenticated.member();
        }
        throw new UnauthenticatedException();
    }
}
