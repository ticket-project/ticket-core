package com.ticket.core.app.auth.command;

import com.ticket.core.app.auth.AuthService;
import com.ticket.core.app.auth.token.AuthTokenManager;
import com.ticket.core.app.auth.token.IssuedAuthTokens;
import com.ticket.core.domain.member.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final AuthService authService;
    private final AuthTokenManager authTokenManager;

    public record Input(
            String email,
            String password
    ) {}
    public record Output(String accessToken,
                         String tokenType,
                         long expiresIn,
                         Long memberId) {
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
    public record Result(Output output, String refreshToken) {
        @Override
        public String toString() {
            return "Result[" +
                    "output=" + output +
                    ", refreshToken=" + redact(refreshToken) +
                    ']';
        }
    }

    public Result execute(final Input input) {
        final Member member = authService.login(input.email(), input.password());
        final IssuedAuthTokens result = authTokenManager.issueTokens(member);
        return new Result(
                new Output(result.accessToken(), result.tokenType(), result.expiresIn(), result.memberId()),
                result.refreshToken()
        );
    }

    private static String redact(final String value) {
        if (value == null) {
            return "null";
        }
        return "[REDACTED]";
    }
}
