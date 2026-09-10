package com.ticket.member.auth.application;

import com.ticket.member.AccessTokenAuthenticator;
import com.ticket.member.AuthenticatedMember;
import com.ticket.member.auth.application.AccessTokenReadResult;
import com.ticket.member.auth.application.AccessTokenReader;
import com.ticket.member.support.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link AccessTokenAuthenticator}의 member 소유 구현이다.
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
