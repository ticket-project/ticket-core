package com.ticket.core.infra.auth.token;

import com.ticket.core.app.auth.token.AuthRefreshToken;
import com.ticket.core.app.auth.token.AuthTokenIssuer;
import com.ticket.core.app.auth.token.IssuedAuthTokens;
import com.ticket.core.app.auth.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
                memberId
        );
    }

    @Override
    public IssuedAuthTokens rotateTokens(
            final Long memberId,
            final String role,
            final AuthRefreshToken refreshToken
    ) {
        final long refreshTokenExpiresIn = jwtProperties.getRefreshTokenExpirationSeconds();
        final String newRefreshToken = refreshTokenStore.rotate(refreshToken, memberId, refreshTokenExpiresIn);
        final String newAccessToken = jwtAccessTokenCodec.createAccessToken(memberId, role);

        return new IssuedAuthTokens(
                newAccessToken,
                newRefreshToken,
                TOKEN_TYPE_BEARER,
                jwtAccessTokenCodec.getAccessTokenExpirationSeconds(),
                refreshTokenExpiresIn,
                memberId
        );
    }
}
