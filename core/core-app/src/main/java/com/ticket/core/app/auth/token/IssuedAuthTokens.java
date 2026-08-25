package com.ticket.core.app.auth.token;

public record IssuedAuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        Long memberId
) {
    @Override
    public String toString() {
        return "IssuedAuthTokens[" +
                "accessToken=" + redact(accessToken) +
                ", refreshToken=" + redact(refreshToken) +
                ", tokenType=" + tokenType +
                ", expiresIn=" + expiresIn +
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
