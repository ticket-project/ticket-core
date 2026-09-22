package com.ticket.booking.admission;

import java.nio.charset.StandardCharsets;

/**
 * admission token 검증에 실제로 쓰는 설정이다.
 *
 * <p>만료 시간은 담지 않는다 — Core는 토큰 자체의 {@code exp} claim으로 만료를 판정하고({@code JwtAdmissionVerifier}가 Jwts 파서에 clock을 넘긴다), 발급
 * 시점의 TTL은 토큰을 만드는 {@code ticket-queue}가 정한다. 설정 키 {@code security.admission.expiration-seconds}는 기존 구성 호환을 위해
 * {@link AdmissionTokenProperties}에 그대로 남아 있지만 Core의 검증 흐름에는 들어오지 않는다.
 */
public record AdmissionTokenSettings(String issuer, String audience, String secretKey) {
    public AdmissionTokenSettings {
        if (isBlank(issuer)) {
            throw new IllegalArgumentException("admission token issuer must not be blank");
        }
        if (isBlank(audience)) {
            throw new IllegalArgumentException("admission token audience must not be blank");
        }
        if (isBlank(secretKey)) {
            throw new IllegalArgumentException("admission token secret key must not be blank");
        }
        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("admission token secret key must be at least 32 bytes for HS256");
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
