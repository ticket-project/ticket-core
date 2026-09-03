package com.ticket.identity.internal.application.auth.command;

import com.ticket.core.support.exception.CoreException;
import com.ticket.core.support.exception.ErrorType;
import com.ticket.identity.internal.application.auth.oauth2.OAuth2AuthCodeStore;
import com.ticket.identity.internal.application.auth.token.AuthTokenIssuer;
import com.ticket.identity.internal.application.auth.token.IssuedAuthTokens;
import com.ticket.identity.internal.domain.member.model.Member;
import com.ticket.identity.internal.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.ticket.shared.RequiredInput;

@Service
@RequiredArgsConstructor
public class ExchangeOAuth2TokenUseCase {

    private final OAuth2AuthCodeStore oAuth2AuthCodeStore;
    private final MemberRepository memberRepository;
    private final AuthTokenIssuer authTokenIssuer;

    public record Input(String code) {
        public Input {
            RequiredInput.notBlank(code, "code");
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
        final Long memberId = oAuth2AuthCodeStore.consumeCode(input.code())
                .orElseThrow(() -> new CoreException(ErrorType.AUTHENTICATION_ERROR, "유효하지 않거나 만료된 인증 코드입니다."));
        final Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND_DATA));
        final IssuedAuthTokens result = authTokenIssuer.issueTokens(member.getId(), member.getRole().name());
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
