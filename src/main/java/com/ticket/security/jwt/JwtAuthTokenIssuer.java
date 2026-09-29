package com.ticket.security.jwt;

import org.springframework.stereotype.Service;

import com.ticket.security.token.AuthTokenIssuer;
import com.ticket.security.token.IssuedAuthTokens;
import com.ticket.security.token.RefreshTokenStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtAuthTokenIssuer implements AuthTokenIssuer {
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private final JwtAccessTokenCodec jwtAccessTokenCodec;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    public IssuedAuthTokens issueTokens(final Long memberId, final String role) {
        final long refreshTokenExpiresIn = jwtProperties.getRefreshTokenExpirationSeconds();
        final String accessToken = jwtAccessTokenCodec.createAccessToken(memberId, role);
        final String refreshToken = refreshTokenStore.createRefreshToken(memberId, refreshTokenExpiresIn);

        return new IssuedAuthTokens(
                accessToken,
                refreshToken,
                TOKEN_TYPE_BEARER,
                jwtAccessTokenCodec.getAccessTokenExpirationSeconds(),
                refreshTokenExpiresIn,
                memberId);
    }
}
