package com.ticket.core.config.security;

import com.ticket.core.app.auth.token.AuthRefreshToken;
import com.ticket.core.app.auth.token.AuthTokenManager;
import com.ticket.core.app.auth.token.IssuedAuthTokens;
import com.ticket.core.app.auth.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtAuthTokenManager implements AuthTokenManager {

    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    public IssuedAuthTokens issueTokens(final Long memberId, final String role) {
        final String accessToken = jwtTokenService.createAccessToken(memberId, role);
        final String refreshToken = refreshTokenStore.createRefreshToken(
                memberId,
                jwtProperties.getRefreshTokenExpirationSeconds()
        );

        return new IssuedAuthTokens(
                accessToken,
                refreshToken,
                TOKEN_TYPE_BEARER,
                jwtTokenService.getAccessTokenExpirationSeconds(),
                memberId
        );
    }

    @Override
    public IssuedAuthTokens rotateTokens(
            final Long memberId,
            final String role,
            final AuthRefreshToken refreshToken
    ) {
        final String newRefreshToken = refreshTokenStore.rotate(
                refreshToken,
                memberId,
                jwtProperties.getRefreshTokenExpirationSeconds()
        );
        final String newAccessToken = jwtTokenService.createAccessToken(memberId, role);

        return new IssuedAuthTokens(
                newAccessToken,
                newRefreshToken,
                TOKEN_TYPE_BEARER,
                jwtTokenService.getAccessTokenExpirationSeconds(),
                memberId
        );
    }
}
