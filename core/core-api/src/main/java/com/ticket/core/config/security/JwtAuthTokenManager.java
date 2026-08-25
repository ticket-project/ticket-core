package com.ticket.core.config.security;

import com.ticket.core.domain.auth.token.AuthRefreshToken;
import com.ticket.core.domain.auth.token.AuthTokenManager;
import com.ticket.core.domain.auth.token.IssuedAuthTokens;
import com.ticket.core.domain.auth.token.RefreshTokenStore;
import com.ticket.core.domain.member.model.Member;
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
    public IssuedAuthTokens issueTokens(final Member member) {
        final String accessToken = jwtTokenService.createAccessToken(member.getId(), member.getRole().name());
        final String refreshToken = refreshTokenStore.createRefreshToken(
                member.getId(),
                jwtProperties.getRefreshTokenExpirationSeconds()
        );

        return new IssuedAuthTokens(
                accessToken,
                refreshToken,
                TOKEN_TYPE_BEARER,
                jwtTokenService.getAccessTokenExpirationSeconds(),
                member.getId()
        );
    }

    @Override
    public IssuedAuthTokens rotateTokens(
            final Member member,
            final AuthRefreshToken refreshToken
    ) {
        final String newRefreshToken = refreshTokenStore.rotate(
                refreshToken,
                member.getId(),
                jwtProperties.getRefreshTokenExpirationSeconds()
        );
        final String newAccessToken = jwtTokenService.createAccessToken(member.getId(), member.getRole().name());

        return new IssuedAuthTokens(
                newAccessToken,
                newRefreshToken,
                TOKEN_TYPE_BEARER,
                jwtTokenService.getAccessTokenExpirationSeconds(),
                member.getId()
        );
    }
}
