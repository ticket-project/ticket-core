package com.ticket.core.domain.auth.token;

import java.util.Optional;

public interface RefreshTokenStore {

    String createRefreshToken(Long memberId, long expirationSeconds);

    Optional<Long> validate(AuthRefreshToken refreshToken);

    Optional<Long> validateWithoutConsume(AuthRefreshToken refreshToken);

    void revoke(AuthRefreshToken refreshToken);

    boolean revokeIfOwned(AuthRefreshToken refreshToken, Long memberId);

    String rotate(AuthRefreshToken refreshToken, Long memberId, long expirationSeconds);
}
