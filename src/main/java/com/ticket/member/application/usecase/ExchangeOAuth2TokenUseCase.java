package com.ticket.member.application.usecase;

import com.ticket.error.InvalidRequestException;
import com.ticket.error.NotFoundException;
import com.ticket.member.application.OAuth2AuthCodeStore;
import com.ticket.member.application.AuthTokenIssuer;
import com.ticket.member.application.IssuedAuthTokens;
import com.ticket.member.domain.Member;
import com.ticket.member.domain.MemberRepository;
import com.ticket.member.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeOAuth2TokenUseCase {

    private final OAuth2AuthCodeStore oAuth2AuthCodeStore;
    private final MemberRepository memberRepository;
    private final AuthTokenIssuer authTokenIssuer;

    public record Input(String code) {
        public Input {
            if (code == null || code.isBlank()) {
                throw new InvalidRequestException("code는 필수입니다.");
            }
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
                .orElseThrow(() -> new UnauthenticatedException("유효하지 않거나 만료된 인증 코드입니다."));
        final Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new NotFoundException());
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
