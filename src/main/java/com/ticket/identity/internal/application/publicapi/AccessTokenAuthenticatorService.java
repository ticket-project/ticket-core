package com.ticket.identity.internal.application.publicapi;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.AccessTokenAuthenticator;
import com.ticket.identity.AuthenticatedMember;
import com.ticket.identity.internal.application.auth.token.AccessTokenReadResult;
import com.ticket.identity.internal.application.auth.token.AccessTokenReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * {@link AccessTokenAuthenticator}의 identity 소유 구현이다.
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
        throw new CoreException(ErrorType.AUTHENTICATION_ERROR);
    }
}
