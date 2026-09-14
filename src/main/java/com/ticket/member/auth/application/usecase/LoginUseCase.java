package com.ticket.member.auth.application.usecase;

import org.springframework.stereotype.Service;

import com.ticket.member.MemberAccountOperations;
import com.ticket.member.MemberStatus;
import com.ticket.member.RawPassword;
import com.ticket.member.auth.application.AuthTokenIssuer;
import com.ticket.member.auth.application.IssuedAuthTokens;
import com.ticket.shared.exception.InvalidRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoginUseCase {
    private final MemberAccountOperations memberAccountOperations;
    private final AuthTokenIssuer authTokenIssuer;

    public record Input(String email, String password) {
        public Input {
            if (email == null || email.isBlank()) {
                throw new InvalidRequestException("email는 필수입니다.");
            }
            if (password == null || password.isBlank()) {
                throw new InvalidRequestException("password는 필수입니다.");
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
        final MemberStatus member =
                memberAccountOperations.authenticate(
                        input.email(), RawPassword.create(input.password()));
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
