package com.ticket.core.app.auth.token;

import com.ticket.core.domain.member.model.Member;

public interface AuthTokenManager {

    IssuedAuthTokens issueTokens(Member member);

    IssuedAuthTokens rotateTokens(Member member, AuthRefreshToken refreshToken);
}
