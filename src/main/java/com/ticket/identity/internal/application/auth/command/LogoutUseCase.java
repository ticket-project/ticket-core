package com.ticket.identity.internal.application.auth.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.internal.application.auth.token.AuthRefreshToken;
import com.ticket.identity.internal.application.auth.token.RefreshTokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ticket.core.app.support.validation.RequiredInput;

@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private final RefreshTokenStore refreshTokenStore;

    public record Input(Long memberId, AuthRefreshToken refreshToken) {
        public Input {
            RequiredInput.positiveId(memberId, "memberId");
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
                    .orElseThrow(() -> new CoreException(ErrorType.AUTHENTICATION_ERROR, "유효하지 않은 리프레시 토큰입니다."));
            if (!tokenOwnerId.equals(input.memberId())) {
                throw new CoreException(ErrorType.AUTHORIZATION_ERROR, "본인 토큰만 무효화할 수 있습니다.");
            }

            throw new CoreException(ErrorType.AUTHENTICATION_ERROR, "이미 무효화된 토큰이거나 처리할 수 없는 상태입니다.");
        }

        return new Output();
    }
}
