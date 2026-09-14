package com.ticket.security.oauth;

import org.springframework.stereotype.Service;

import com.ticket.member.MemberAccountOperations;
import com.ticket.member.MemberStatus;
import com.ticket.member.exception.UnauthenticatedException;
import com.ticket.security.token.AuthTokenIssuer;
import com.ticket.security.token.IssuedAuthTokens;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExchangeOAuth2TokenUseCase {
    private final OAuth2AuthCodeStore oauth2AuthCodeStore;
    private final MemberAccountOperations memberAccountOperations;
    private final AuthTokenIssuer authTokenIssuer;

    public record Input(String code) {
        public Input {
            if (code == null || code.isBlank()) {
                throw new InvalidRequestException("code는 필수입니다.");
            }
        }
    }

    public record Output(String accessToken, String tokenType, long expiresIn, Long memberId) {
        @Override
        public String toString() {
            return "Output["
                    + "accessToken="
                    + redact(accessToken)
                    + ", tokenType="
                    + tokenType
                    + ", expiresIn="
                    + expiresIn
                    + ", memberId="
                    + memberId
                    + ']';
        }
    }

    public record Result(Output output, String refreshToken, long refreshTokenExpiresIn) {
        @Override
        public String toString() {
            return "Result["
                    + "output="
                    + output
                    + ", refreshToken="
                    + redact(refreshToken)
                    + ", refreshTokenExpiresIn="
                    + refreshTokenExpiresIn
                    + ']';
        }
    }

    public Result execute(final Input input) {
        final Long memberId =
                oauth2AuthCodeStore
                        .consumeCode(input.code())
                        .orElseThrow(() -> new UnauthenticatedException("유효하지 않거나 만료된 인증 코드입니다."));
        final MemberStatus member = memberAccountOperations.requireActiveIdentity(memberId);
        final IssuedAuthTokens result =
                authTokenIssuer.issueTokens(member.memberId(), member.role());
        return new Result(
                new Output(
                        result.accessToken(),
                        result.tokenType(),
                        result.expiresIn(),
                        result.memberId()),
                result.refreshToken(),
                result.refreshTokenExpiresIn());
    }

    private static String redact(final String value) {
        if (value == null) {
            return "null";
        }
        return "[REDACTED]";
    }
}
