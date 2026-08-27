package com.ticket.core.app.auth.command;

import com.ticket.support.error.CoreException;
import com.ticket.core.app.error.ApplicationErrorType;
import com.ticket.core.domain.auth.oauth2.OAuth2AuthCodeStore;
import com.ticket.core.domain.auth.token.AuthTokenManager;
import com.ticket.core.domain.auth.token.IssuedAuthTokens;
import com.ticket.core.domain.member.model.Member;
import com.ticket.core.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeOAuth2TokenUseCase {

    private final OAuth2AuthCodeStore oAuth2AuthCodeStore;
    private final MemberRepository memberRepository;
    private final AuthTokenManager authTokenManager;

    public record Input(String code) {}

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
        final Long memberId = oAuth2AuthCodeStore.consumeCode(input.code())
                .orElseThrow(() -> new CoreException(ApplicationErrorType.AUTHENTICATION_FAILED, "유효하지 않거나 만료된 인증 코드입니다."));
        final Member member = memberRepository.getActiveById(memberId);
        final IssuedAuthTokens result = authTokenManager.issueTokens(member.getId(), member.getRole().name());
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
