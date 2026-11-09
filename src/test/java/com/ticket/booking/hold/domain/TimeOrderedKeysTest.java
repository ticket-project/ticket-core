package com.ticket.booking.hold.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
class TimeOrderedKeysTest {
    private static final Instant NOW = Instant.parse("2026-11-09T07:00:00Z");

    @Test
    void 접두어_뒤에_하이픈_없는_32글자_UUIDv7이다() {
        final String key = new TimeOrderedKeys(Clock.fixed(NOW, ZoneOffset.UTC)).next("ORDER-");

        assertThat(key).startsWith("ORDER-");
        final String hex = key.substring("ORDER-".length());
        assertThat(hex).hasSize(32).matches("[0-9a-f]{32}");
        final UUID uuid = UUID.fromString(hex.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
        assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void 시각이_늦을수록_키도_크다() {
        final String earlier = new TimeOrderedKeys(Clock.fixed(NOW, ZoneOffset.UTC)).next("HOLD-");
        final String later = new TimeOrderedKeys(Clock.fixed(NOW.plusMillis(1), ZoneOffset.UTC)).next("HOLD-");

        assertThat(later).isGreaterThan(earlier);
    }
}
