package com.ticket.security.auth;

import static com.ticket.shared.api.InputChecks.requirePositiveId;

import org.springframework.stereotype.Service;

import com.ticket.security.exception.AuthorizationException;
import com.ticket.security.exception.UnauthenticatedException;
import com.ticket.security.token.AuthRefreshToken;
import com.ticket.security.token.RefreshTokenStore;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LogoutUseCase {
    private final RefreshTokenStore refreshTokenStore;

    public record Input(Long memberId, AuthRefreshToken refreshToken) {
        public Input {
            requirePositiveId(memberId, "memberId");
        }

        /** API 경계에서 받은 원문을 값 객체로 바꾼다. */
        public static Input of(final Long memberId, final String rawRefreshToken) {
            return new Input(memberId, AuthRefreshToken.from(rawRefreshToken));
        }
    }

    public record Output() {}

    public Output execute(final Input input) {
        final boolean revoked = refreshTokenStore.revokeIfOwned(input.refreshToken(), input.memberId());
        if (!revoked) {
            final Long tokenOwnerId = refreshTokenStore
                    .validateWithoutConsume(input.refreshToken())
                    .orElseThrow(() -> new UnauthenticatedException("유효하지 않은 리프레시 토큰입니다."));
            if (!tokenOwnerId.equals(input.memberId())) {
                throw new AuthorizationException("본인 토큰만 무효화할 수 있습니다.");
            }

            throw new UnauthenticatedException("이미 무효화된 토큰이거나 처리할 수 없는 상태입니다.");
        }

        return new Output();
    }
}
