package com.ticket.core.domain.performanceseat.support;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 좌석 관련 Redis key 규칙을 한 곳에서 관리한다.
 */
public final class SeatRedisKey {

    private static final String SELECT_KEY = "seat:select:{perf:%d}:%d";
    private static final String SELECT_PATTERN = "seat:select:{perf:%d}:*";
    private static final String SELECT_SEAT_INDEX_KEY = "seat:select:index:{perf:%d}";

    private static final String HOLD_KEY = "seat:hold:{perf:%d}:%d";
    private static final String HOLD_PATTERN = "seat:hold:{perf:%d}:*";
    private static final String HOLD_SEAT_INDEX_KEY = "seat:hold:index:{perf:%d}";
    private static final String HOLD_META_KEY = "hold:key:%s";
    private static final Pattern SELECT_KEY_PATTERN = Pattern.compile("^seat:select:\\{perf:(\\d+)}:(\\d+)$");
    private static final Pattern HOLD_KEY_PATTERN = Pattern.compile("^seat:hold:\\{perf:(\\d+)}:(\\d+)$");
    private static final Pattern HOLD_META_KEY_PATTERN = Pattern.compile("^hold:key:(.+)$");

    private SeatRedisKey() {}

    public static String select(final Long perfId, final Long seatId) {
        return String.format(SELECT_KEY, perfId, seatId);
    }

    public static String selectPattern(final Long perfId) {
        return String.format(SELECT_PATTERN, perfId);
    }

    public static String selectSeatIndex(final Long perfId) {
        return String.format(SELECT_SEAT_INDEX_KEY, perfId);
    }

    public static String hold(final Long perfId, final Long seatId) {
        return String.format(HOLD_KEY, perfId, seatId);
    }

    public static String holdPattern(final Long perfId) {
        return String.format(HOLD_PATTERN, perfId);
    }

    public static String holdMeta(final String holdKey) {
        return String.format(HOLD_META_KEY, holdKey);
    }

    public static String holdSeatIndex(final Long perfId) {
        return String.format(HOLD_SEAT_INDEX_KEY, perfId);
    }

    public static Optional<SelectKey> tryParseSelectKey(final String key) {
        return match(SELECT_KEY_PATTERN, key)
                .map(matcher -> new SelectKey(
                        Long.parseLong(matcher.group(1)),
                        Long.parseLong(matcher.group(2))
                ));
    }

    public static SelectKey parseSelectKey(final String key) {
        return tryParseSelectKey(key)
                .orElseThrow(() -> new IllegalArgumentException("유효한 select key가 아닙니다. key=" + key));
    }

    public static Optional<HoldKey> tryParseHoldKey(final String key) {
        return match(HOLD_KEY_PATTERN, key)
                .map(matcher -> new HoldKey(
                        Long.parseLong(matcher.group(1)),
                        Long.parseLong(matcher.group(2))
                ));
    }

    public static HoldKey parseHoldKey(final String key) {
        return tryParseHoldKey(key)
                .orElseThrow(() -> new IllegalArgumentException("유효한 hold key가 아닙니다. key=" + key));
    }

    public static Optional<HoldMetaKey> tryParseHoldMetaKey(final String key) {
        return match(HOLD_META_KEY_PATTERN, key)
                .map(matcher -> new HoldMetaKey(matcher.group(1)));
    }

    public static HoldMetaKey parseHoldMetaKey(final String key) {
        return tryParseHoldMetaKey(key)
                .orElseThrow(() -> new IllegalArgumentException("유효한 hold meta key가 아닙니다. key=" + key));
    }

    private static Optional<Matcher> match(final Pattern pattern, final String key) {
        if (key == null) {
            return Optional.empty();
        }
        final Matcher matcher = pattern.matcher(key);
        return matcher.matches() ? Optional.of(matcher) : Optional.empty();
    }

    public record SelectKey(Long performanceId, Long seatId) {}

    public record HoldKey(Long performanceId, Long seatId) {}

    public record HoldMetaKey(String holdKey) {}
}
