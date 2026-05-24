package com.ticket.loadtest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoadTestTokens {

    private static final Pattern SUBJECT_PATTERN = Pattern.compile("\"sub\"\\s*:\\s*\"?([^\",}]+)\"?");

    private LoadTestTokens() {
    }

    public static String createAccessToken(
            final String issuer,
            final String secret,
            final Long memberId,
            final String role,
            final Instant now,
            final long ttlSeconds
    ) {
        final long issuedAt = now.getEpochSecond();
        final long expiresAt = issuedAt + ttlSeconds;
        final String payload = "{\"iss\":\"" + escapeJson(issuer)
                + "\",\"sub\":\"" + memberId
                + "\",\"role\":\"" + escapeJson(role)
                + "\",\"iat\":" + issuedAt
                + ",\"exp\":" + expiresAt + "}";
        return createSignedToken(payload, secret);
    }

    public static String createAdmissionToken(
            final String issuer,
            final String audience,
            final String secret,
            final Long memberId,
            final Long performanceId,
            final Instant now,
            final long ttlSeconds
    ) {
        final long issuedAt = now.getEpochSecond();
        final long expiresAt = issuedAt + ttlSeconds;
        final String payload = "{\"iss\":\"" + escapeJson(issuer)
                + "\",\"aud\":[\"" + escapeJson(audience)
                + "\"],\"sub\":\"" + memberId
                + "\",\"performanceId\":" + performanceId
                + ",\"scope\":\"ticket-admission\""
                + ",\"iat\":" + issuedAt
                + ",\"exp\":" + expiresAt
                + ",\"jti\":\"" + UUID.randomUUID() + "\"}";
        return createSignedToken(payload, secret);
    }

    public static Long readSubjectAsLong(final String token) {
        final String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("JWT must contain header and payload");
        }

        final String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        final Matcher matcher = SUBJECT_PATTERN.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String createSignedToken(final String payload, final String secret) {
        final String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        final String unsignedToken = base64Url(header.getBytes(StandardCharsets.UTF_8))
                + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return unsignedToken + "." + sign(unsignedToken, secret);
    }

    private static String sign(final String unsignedToken, final String secret) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign JWT", exception);
        }
    }

    private static String base64Url(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String escapeJson(final CharSequence value) {
        return value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
