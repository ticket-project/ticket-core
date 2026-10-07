package com.ticket.security.token;

import org.springframework.stereotype.Service;

import com.ticket.member.api.AuthenticatedMember;
import com.ticket.security.api.AccessTokenAuthenticationApi;
import com.ticket.security.exception.AuthException;
import com.ticket.security.exception.SecurityErrorCode;
import com.ticket.security.jwt.JwtAccessTokenCodec;

import lombok.RequiredArgsConstructor;

/**
 * {@link AccessTokenAuthenticationApi}의 security 소유 구현이다. HTTP filter chain을 타지 않는 경로(WebSocket STOMP CONNECT)가 쓴다 —
 * HTTP 요청은 {@code AccessTokenAuthenticationFilter}가 {@link JwtAccessTokenCodec}의 3-way 결과를 직접 읽는다(401 안내 문구가 만료·무효로
 * 갈린다). 이 계약은 구분이 필요 없는 호출부를 위한 것이라 실패를 하나로 합쳐 내보낸다.
 *
 * <p><b>토큰의 서명·만료만 본다. 회원 DB를 조회하지 않는다.</b> 탈퇴한 회원의 토큰도 만료 전까지는 통과한다 — 탈퇴 회원이 막혀야 하는 곳은 좌석을 실제로 점유하는 주문 생성(booking)과 토큰
 * 재발급·로그인이고, 그곳들이 직접 활성 회원을 확인한다.
 */
@Service
@RequiredArgsConstructor
public class AccessTokenAuthenticatorService implements AccessTokenAuthenticationApi {
    private final JwtAccessTokenCodec accessTokenReader;

    @Override
    public AuthenticatedMember authenticate(final String accessToken) {
        if (accessTokenReader.read(accessToken) instanceof AccessTokenReadResult.Authenticated authenticated) {
            return authenticated.member();
        }
        throw new AuthException(SecurityErrorCode.E1000);
    }
}
