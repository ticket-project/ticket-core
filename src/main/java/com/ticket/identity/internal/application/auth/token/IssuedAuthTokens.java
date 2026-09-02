package com.ticket.identity.internal.application.auth.token;

/**
 * 발급된 인증 토큰 한 쌍이다. 두 토큰의 만료를 모두 담아, 토큰을 발급한 쪽이 정한 값을
 * 그대로 쓰게 한다. 리프레시 쿠키의 max-age가 저장소 TTL과 어긋나지 않도록 하기 위함이다.
 */
public record IssuedAuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshTokenExpiresIn,
        Long memberId
) {
    @Override
    public String toString() {
        return "IssuedAuthTokens[" +
                "accessToken=" + redact(accessToken) +
                ", refreshToken=" + redact(refreshToken) +
                ", tokenType=" + tokenType +
                ", expiresIn=" + expiresIn +
                ", refreshTokenExpiresIn=" + refreshTokenExpiresIn +
                ", memberId=" + memberId +
                ']';
    }

    private static String redact(final String value) {
        if (value == null) {
            return "null";
        }
        return "[REDACTED]";
    }
}
