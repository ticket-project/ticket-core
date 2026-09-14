package com.ticket.security.token;

import org.springframework.stereotype.Service;

import com.ticket.member.AuthenticatedMember;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.security.AccessTokenAuthenticator;

import lombok.RequiredArgsConstructor;

/** {@link AccessTokenAuthenticator}의 member 소유 구현이다. */
@Service
@RequiredArgsConstructor
public class AccessTokenAuthenticatorService implements AccessTokenAuthenticator {
    private final AccessTokenReader accessTokenReader;

    @Override
    public AuthenticatedMember authenticate(final String accessToken) {
        if (accessTokenReader.read(accessToken)
                instanceof AccessTokenReadResult.Authenticated authenticated) {
            return authenticated.member();
        }
        throw new UnauthenticatedException();
    }
}
