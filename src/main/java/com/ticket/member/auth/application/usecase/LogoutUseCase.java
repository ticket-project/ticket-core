package com.ticket.member.auth.application.usecase;

import com.ticket.error.InvalidRequestException;
import com.ticket.member.auth.application.AuthRefreshToken;
import com.ticket.member.auth.application.RefreshTokenStore;
import com.ticket.member.exception.AuthorizationException;
import com.ticket.member.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private final RefreshTokenStore refreshTokenStore;

    public record Input(Long memberId, AuthRefreshToken refreshToken) {
        public Input {
            if (memberId == null) {
                throw new InvalidRequestException("memberId는 필수입니다.");
            }
            if (memberId <= 0) {
                throw new InvalidRequestException("memberId는 양수여야 합니다.");
            }
        }

        /**
         * API 경계에서 받은 원문을 값 객체로 바꾼다.
         */
        public static Input of(final Long memberId, final String rawRefreshToken) {
            return new Input(memberId, AuthRefreshToken.from(rawRefreshToken));
        }
    }

    public record Output() {}

    public Output execute(final Input input) {
        final boolean revoked = refreshTokenStore.revokeIfOwned(input.refreshToken(), input.memberId());
        if (!revoked) {
            final Long tokenOwnerId = refreshTokenStore.validateWithoutConsume(input.refreshToken())
                    .orElseThrow(() -> new UnauthenticatedException("유효하지 않은 리프레시 토큰입니다."));
            if (!tokenOwnerId.equals(input.memberId())) {
                throw new AuthorizationException("본인 토큰만 무효화할 수 있습니다.");
            }

            throw new UnauthenticatedException("이미 무효화된 토큰이거나 처리할 수 없는 상태입니다.");
        }

        return new Output();
    }
}
