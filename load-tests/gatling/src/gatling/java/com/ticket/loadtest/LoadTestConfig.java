package com.ticket.loadtest;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.OpenInjectionStep;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class LoadTestConfig {
    private static final AtomicInteger LOGIN_COUNTER = new AtomicInteger(intProperty(ConfigKey.LOGIN_START_INDEX));
    private static final AtomicInteger TOKEN_COUNTER = new AtomicInteger();
    private static final AtomicInteger QUEUE_TOKEN_COUNTER = new AtomicInteger();
    private static final AtomicLong SYNTHETIC_MEMBER_COUNTER =
            new AtomicLong(longProperty(ConfigKey.SYNTHETIC_MEMBER_START_ID));

    private LoadTestConfig() {
    }

    public static String baseUrl() {
        return property(ConfigKey.BASE_URL);
    }

    public static String performanceId() {
        return property(ConfigKey.PERFORMANCE_ID);
    }

    public static int statusPolls() {
        return intProperty(ConfigKey.STATUS_POLLS);
    }

    public static int statusPollPauseSeconds() {
        return intProperty(ConfigKey.STATUS_POLL_PAUSE_SECONDS);
    }

    public static OpenInjectionStep injection() {
        final int users = intProperty(ConfigKey.USERS);
        final int durationSeconds = intProperty(ConfigKey.DURATION_SECONDS);
        final String mode = property(ConfigKey.INJECTION_MODE).toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "at-once-users" -> atOnceUsers(users);
            case "constant-users-per-sec" -> constantUsersPerSec(doubleProperty(ConfigKey.USERS_PER_SECOND))
                    .during(Duration.ofSeconds(durationSeconds));
            case "ramp-users-per-sec" -> rampUsersPerSec(doubleProperty(ConfigKey.USERS_PER_SECOND))
                    .to(doubleProperty(ConfigKey.TARGET_USERS_PER_SECOND))
                    .during(Duration.ofSeconds(durationSeconds));
            default -> rampUsers(users).during(Duration.ofSeconds(durationSeconds));
        };
    }

    public static ChainBuilder initializeSession() {
        return exec(session -> session
                .set("performanceId", performanceId())
                .set("seatIdsJson", seatIdsJsonArray()));
    }

    public static ChainBuilder authenticate() {
        final String mode = property(ConfigKey.ACCESS_TOKEN_MODE).toLowerCase(Locale.ROOT);
        if ("tokens".equals(mode)) {
            return exec(session -> session.set("accessToken", nextFromCsv(ConfigKey.ACCESS_TOKENS, TOKEN_COUNTER)));
        }
        if ("synthetic-jwt".equals(mode)) {
            return exec(session -> session.set("accessToken", createSyntheticJwt(nextSyntheticMember())));
        }
        return exec(session -> {
            final int loginIndex = LOGIN_COUNTER.getAndIncrement();
            final String email = property(ConfigKey.LOGIN_EMAIL_PREFIX)
                    + loginIndex + "@" + property(ConfigKey.LOGIN_EMAIL_DOMAIN);
            return session.set("loginEmail", email)
                    .set("loginPassword", property(ConfigKey.LOGIN_PASSWORD));
        }).exec(http("login")
                .post("/api/v1/auth/login")
                .body(StringBody("""
                        {
                          "email": "#{loginEmail}",
                          "password": "#{loginPassword}"
                        }
                        """))
                .check(status().is(200))
                .check(io.gatling.javaapi.core.CoreDsl.jsonPath("$.result").is("SUCCESS"))
                .check(io.gatling.javaapi.core.CoreDsl.jsonPath("$.data.accessToken").saveAs("accessToken")));
    }

    public static ChainBuilder withConfiguredQueueToken() {
        return exec(session -> session.set("queueToken", nextFromCsv(ConfigKey.QUEUE_TOKENS, QUEUE_TOKEN_COUNTER)));
    }

    public static Map<CharSequence, String> authHeaders() {
        return Map.of("Authorization", "Bearer #{accessToken}");
    }

    public static Map<CharSequence, String> authAndQueueHeaders() {
        return Map.of(
                "Authorization", "Bearer #{accessToken}",
                "X-Queue-Token", "#{queueToken}"
        );
    }

    private static String property(final ConfigKey key) {
        final String value = System.getProperty(key.propertyName());
        if (value == null || value.isBlank()) {
            final String defaultValue = key.defaultValue();
            if (defaultValue == null) {
                throw new IllegalStateException("Missing required system property: -D" + key.propertyName());
            }
            return defaultValue;
        }
        return value.trim();
    }

    private static int intProperty(final ConfigKey key) {
        return Integer.parseInt(property(key));
    }

    private static long longProperty(final ConfigKey key) {
        return Long.parseLong(property(key));
    }

    private static double doubleProperty(final ConfigKey key) {
        return Double.parseDouble(property(key));
    }

    private static String nextFromCsv(final ConfigKey key, final AtomicInteger counter) {
        final CsvValues csvValues = parseCsv(key);
        return csvValues.values().get(Math.floorMod(counter.getAndIncrement(), csvValues.values().size()));
    }

    private static CsvValues parseCsv(final ConfigKey key) {
        final List<String> values = Stream.of(property(key).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (values.isEmpty()) {
            throw new IllegalStateException("System property must contain at least one value: -D" + key.propertyName());
        }
        return new CsvValues(values);
    }

    private static String seatIdsJsonArray() {
        return Stream.of(property(ConfigKey.SEAT_IDS).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static SyntheticMemberId nextSyntheticMember() {
        return new SyntheticMemberId(SYNTHETIC_MEMBER_COUNTER.getAndIncrement());
    }

    private static String createSyntheticJwt(final SyntheticMemberId memberId) {
        try {
            final Instant now = Instant.now();
            final long issuedAt = now.getEpochSecond();
            final long expiresAt = issuedAt + intProperty(ConfigKey.SYNTHETIC_TOKEN_TTL_SECONDS);
            final String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            final String payload = "{\"iss\":\"" + escapeJson(property(ConfigKey.JWT_ISSUER))
                    + "\",\"sub\":\"" + memberId.value()
                    + "\",\"role\":\"" + escapeJson(property(ConfigKey.SYNTHETIC_JWT_ROLE))
                    + "\",\"iat\":" + issuedAt
                    + ",\"exp\":" + expiresAt + "}";
            final String unsignedToken = base64Url(header.getBytes(StandardCharsets.UTF_8))
                    + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(property(ConfigKey.JWT_SECRET)
                    .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return unsignedToken + "." + base64Url(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create synthetic JWT", exception);
        }
    }

    private static String base64Url(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String escapeJson(final CharSequence value) {
        return value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record CsvValues(List<String> values) {
    }

    private record SyntheticMemberId(Long value) {
    }

    private enum ConfigKey {
        BASE_URL,
        PERFORMANCE_ID,
        STATUS_POLLS,
        STATUS_POLL_PAUSE_SECONDS,
        USERS,
        DURATION_SECONDS,
        INJECTION_MODE,
        USERS_PER_SECOND,
        TARGET_USERS_PER_SECOND,
        ACCESS_TOKEN_MODE,
        ACCESS_TOKENS,
        QUEUE_TOKENS,
        LOGIN_EMAIL_PREFIX,
        LOGIN_EMAIL_DOMAIN,
        LOGIN_PASSWORD,
        LOGIN_START_INDEX,
        SEAT_IDS,
        SYNTHETIC_MEMBER_START_ID,
        SYNTHETIC_TOKEN_TTL_SECONDS,
        JWT_ISSUER,
        SYNTHETIC_JWT_ROLE,
        JWT_SECRET;

        private String propertyName() {
            return switch (this) {
                case BASE_URL -> "baseUrl";
                case PERFORMANCE_ID -> "performanceId";
                case STATUS_POLLS -> "statusPolls";
                case STATUS_POLL_PAUSE_SECONDS -> "statusPollPauseSeconds";
                case USERS -> "users";
                case DURATION_SECONDS -> "durationSeconds";
                case INJECTION_MODE -> "injectionMode";
                case USERS_PER_SECOND -> "usersPerSecond";
                case TARGET_USERS_PER_SECOND -> "targetUsersPerSecond";
                case ACCESS_TOKEN_MODE -> "accessTokenMode";
                case ACCESS_TOKENS -> "accessTokens";
                case QUEUE_TOKENS -> "queueTokens";
                case LOGIN_EMAIL_PREFIX -> "loginEmailPrefix";
                case LOGIN_EMAIL_DOMAIN -> "loginEmailDomain";
                case LOGIN_PASSWORD -> "loginPassword";
                case LOGIN_START_INDEX -> "loginStartIndex";
                case SEAT_IDS -> "seatIds";
                case SYNTHETIC_MEMBER_START_ID -> "syntheticMemberStartId";
                case SYNTHETIC_TOKEN_TTL_SECONDS -> "syntheticTokenTtlSeconds";
                case JWT_ISSUER -> "jwtIssuer";
                case SYNTHETIC_JWT_ROLE -> "syntheticJwtRole";
                case JWT_SECRET -> "jwtSecret";
            };
        }

        private String defaultValue() {
            return switch (this) {
                case BASE_URL -> "http://localhost:8080";
                case PERFORMANCE_ID -> "1";
                case STATUS_POLLS -> "3";
                case STATUS_POLL_PAUSE_SECONDS -> "1";
                case USERS -> "10";
                case DURATION_SECONDS -> "10";
                case INJECTION_MODE -> "ramp-users";
                case USERS_PER_SECOND -> "1.0";
                case TARGET_USERS_PER_SECOND -> "10.0";
                case ACCESS_TOKEN_MODE -> "login";
                case LOGIN_EMAIL_PREFIX -> "loadtest";
                case LOGIN_EMAIL_DOMAIN -> "test.com";
                case LOGIN_PASSWORD -> "password1234";
                case LOGIN_START_INDEX -> "1";
                case SEAT_IDS -> "1";
                case SYNTHETIC_MEMBER_START_ID -> "1";
                case SYNTHETIC_TOKEN_TTL_SECONDS -> "3600";
                case JWT_ISSUER -> "ticket";
                case SYNTHETIC_JWT_ROLE -> "MEMBER";
                case ACCESS_TOKENS, QUEUE_TOKENS, JWT_SECRET -> null;
            };
        }
    }
}
