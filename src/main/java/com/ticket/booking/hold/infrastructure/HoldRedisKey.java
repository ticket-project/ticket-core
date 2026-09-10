package com.ticket.booking.hold.infrastructure;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * hold 관련 Redis key 규칙을 한 곳에서 관리한다.
 *
 * <p>원래는 {@code SeatSelectionRedisKey}(selection 소유)에 selection·hold 키가 함께 있었다.
 * hold를 자기 패키지로 모으며 key 형식은 그대로 두고 파싱 책임만 여기로 옮겼다 — 운영 중인 Redis
 * key 문자열 형식은 바뀌지 않는다.
 */
public final class HoldRedisKey {

    private static final String HOLD_KEY = "seat:hold:{perf:%d}:%d";
    private static final String HOLD_SEAT_INDEX_KEY = "seat:hold:index:{perf:%d}";
    private static final String HOLD_META_KEY = "hold:key:%s";
    private static final Pattern HOLD_META_KEY_PATTERN = Pattern.compile("^hold:key:(.+)$");

    private HoldRedisKey() {}

    public static String hold(final Long perfId, final Long seatId) {
        return String.format(HOLD_KEY, perfId, seatId);
    }

    public static String holdMeta(final String holdKey) {
        return String.format(HOLD_META_KEY, holdKey);
    }

    public static String holdSeatIndex(final Long perfId) {
        return String.format(HOLD_SEAT_INDEX_KEY, perfId);
    }

    public static Optional<HoldMetaKey> tryParseHoldMetaKey(final String key) {
        if (key == null) {
            return Optional.empty();
        }
        final Matcher matcher = HOLD_META_KEY_PATTERN.matcher(key);
        return matcher.matches() ? Optional.of(new HoldMetaKey(matcher.group(1))) : Optional.empty();
    }

    public record HoldMetaKey(String holdKey) {}
}
