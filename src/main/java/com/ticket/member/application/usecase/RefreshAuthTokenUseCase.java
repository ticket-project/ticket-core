package com.ticket.member.application.usecase;

import com.ticket.error.NotFoundException;
import com.ticket.member.application.AuthRefreshToken;
import com.ticket.member.application.AuthTokenIssuer;
import com.ticket.member.application.IssuedAuthTokens;
import com.ticket.member.application.RefreshTokenStore;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshAuthTokenUseCase {

    private final RefreshTokenStore refreshTokenStore;
    private final MemberRepository memberRepository;
    private final AuthTokenIssuer authTokenIssuer;

    public record Input(AuthRefreshToken refreshToken) {

        /**
         * API 경계에서 받은 원문을 값 객체로 바꾼다. 컨트롤러가 도메인 타입을 알지 않아도 되고,
         * 형식이 올바르지 않으면 이 지점에서 인증 오류로 걸린다.
         */
        public static Input of(final String rawRefreshToken) {
            return new Input(AuthRefreshToken.from(rawRefreshToken));
        }
    }

    public record Output(
            String accessToken,
            String tokenType,
            long expiresIn,
            Long memberId
    ) {
        @Override
        public String toString() {
            return "Output[" +
                    "accessToken=" + redact(accessToken) +
                    ", tokenType=" + tokenType +
                    ", expiresIn=" + expiresIn +
                    ", memberId=" + memberId +
                    ']';
        }
    }

    public record Result(Output output, String refreshToken, long refreshTokenExpiresIn) {
        @Override
        public String toString() {
            return "Result[" +
                    "output=" + output +
                    ", refreshToken=" + redact(refreshToken) +
                    ", refreshTokenExpiresIn=" + refreshTokenExpiresIn +
                    ']';
        }
    }

    public Result execute(final Input input) {
        final Long memberId = refreshTokenStore.validate(input.refreshToken())
                .orElseThrow(() -> new UnauthenticatedException("유효하지 않거나 만료된 리프레시 토큰입니다."));
        final Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new NotFoundException());
        final IssuedAuthTokens result = authTokenIssuer.rotateTokens(member.getId(), member.getRole().name(), input.refreshToken());
        return new Result(
                new Output(result.accessToken(), result.tokenType(), result.expiresIn(), result.memberId()),
                result.refreshToken(),
                result.refreshTokenExpiresIn()
        );
    }

    private static String redact(final String value) {
        if (value == null) {
            return "null";
        }
        return "[REDACTED]";
    }
}
