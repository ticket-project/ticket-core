package com.ticket.security.token;

import java.util.Optional;

public interface RefreshTokenStore {
    String createRefreshToken(Long memberId, long expirationSeconds);

    /** 토큰을 읽는 즉시 지운다. 같은 토큰으로 두 번 재발급할 수 없다. */
    Optional<Long> consume(AuthRefreshToken refreshToken);

    Optional<Long> validateWithoutConsume(AuthRefreshToken refreshToken);

    boolean revokeIfOwned(AuthRefreshToken refreshToken, Long memberId);
}
