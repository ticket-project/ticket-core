package com.ticket.loadtest;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadTestTokensTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");

    @Test
    void createsAdmissionTokenMatchingTicketAdmissionContract() {
        String token = LoadTestTokens.createAdmissionToken(
                "ticket-queue",
                "ticket-api",
                SECRET,
                101L,
                10L,
                NOW,
                300L
        );

        String payload = decodePart(token, 1);

        assertTrue(payload.contains("\"iss\":\"ticket-queue\""));
        assertTrue(payload.contains("\"aud\":[\"ticket-api\"]"));
        assertTrue(payload.contains("\"sub\":\"101\""));
        assertTrue(payload.contains("\"performanceId\":10"));
        assertTrue(payload.contains("\"scope\":\"ticket-admission\""));
        assertTrue(payload.contains("\"iat\":1779624000"));
        assertTrue(payload.contains("\"exp\":1779624300"));
        assertSignature(token);
    }

    @Test
    void readsSubjectFromJwtPayload() {
        String token = LoadTestTokens.createAccessToken("ticket", SECRET, 202L, "MEMBER", NOW, 3600L);

        assertEquals(202L, LoadTestTokens.readSubjectAsLong(token));
    }

    private static String decodePart(final String token, final int index) {
        String[] parts = token.split("\\.");
        return new String(Base64.getUrlDecoder().decode(parts[index]), StandardCharsets.UTF_8);
    }

    private static void assertSignature(final String token) {
        String[] parts = token.split("\\.");
        String unsignedToken = parts[0] + "." + parts[1];
        assertEquals(sign(unsignedToken), parts[2]);
    }

    private static String sign(final String unsignedToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
