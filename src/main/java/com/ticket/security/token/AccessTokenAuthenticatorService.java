package com.ticket.security.token;

import org.springframework.stereotype.Service;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.member.api.MemberAccountApi;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.security.api.AccessTokenAuthenticationApi;
import com.ticket.shared.exception.NotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * {@link AccessTokenAuthenticationApi}의 security 소유 구현이다.
 *
 * <p>만료와 무효를 구분하지 않고 같은 인증 실패로 다룬다. 구분이 필요한 것은 HTTP 경로뿐이고(401 응답의 안내 문구가 갈린다), 그쪽은 {@link AccessTokenReader}의 3-way 결과를
 * 직접 읽는다. 이 계약은 구분이 필요 없는 호출부(WebSocket STOMP CONNECT 등)를 위한 것이라 실패를 하나로 합쳐 내보낸다.
 */
@Service
@RequiredArgsConstructor
public class AccessTokenAuthenticatorService implements AccessTokenAuthenticationApi {
    private final AccessTokenReader accessTokenReader;
    private final MemberAccountApi memberAccountApi;

    /** 유효한 JWT에 대해서만 현재 회원 상태를 확인한다. 회원 조회 장애는 인증 성공으로 바꾸지 않는다. */
    public AccessTokenReadResult read(final String accessToken) {
        final AccessTokenReadResult result = accessTokenReader.read(accessToken);
        if (result instanceof AccessTokenReadResult.Authenticated authenticated) {
            try {
                memberAccountApi.getActiveIdentity(authenticated.member().memberId());
            } catch (final NotFoundException exception) {
                return AccessTokenReadResult.invalid();
            }
        }
        return result;
    }

    @Override
    public AuthenticatedMember authenticate(final String accessToken) {
        if (read(accessToken) instanceof AccessTokenReadResult.Authenticated authenticated) {
            return authenticated.member();
        }
        throw new UnauthenticatedException();
    }
}
