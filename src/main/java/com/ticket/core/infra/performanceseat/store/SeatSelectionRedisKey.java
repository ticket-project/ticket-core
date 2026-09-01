package com.ticket.core.infra.performanceseat.store;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 좌석 관련 Redis key 규칙을 한 곳에서 관리한다.
 */
public final class SeatSelectionRedisKey {

    private static final String SELECT_KEY = "seat:select:{perf:%d}:%d";
    private static final String SELECT_SEAT_INDEX_KEY = "seat:select:index:{perf:%d}";

    private static final String HOLD_KEY = "seat:hold:{perf:%d}:%d";
    private static final String HOLD_SEAT_INDEX_KEY = "seat:hold:index:{perf:%d}";
    private static final String HOLD_META_KEY = "hold:key:%s";
    private static final Pattern SELECT_KEY_PATTERN = Pattern.compile("^seat:select:\\{perf:(\\d+)}:(\\d+)$");
    private static final Pattern HOLD_META_KEY_PATTERN = Pattern.compile("^hold:key:(.+)$");

    private SeatSelectionRedisKey() {}

    public static String select(final Long perfId, final Long seatId) {
        return String.format(SELECT_KEY, perfId, seatId);
    }

    public static String selectSeatIndex(final Long perfId) {
        return String.format(SELECT_SEAT_INDEX_KEY, perfId);
    }

    public static String hold(final Long perfId, final Long seatId) {
        return String.format(HOLD_KEY, perfId, seatId);
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

    public static Optional<HoldMetaKey> tryParseHoldMetaKey(final String key) {
        return match(HOLD_META_KEY_PATTERN, key)
                .map(matcher -> new HoldMetaKey(matcher.group(1)));
    }

    private static Optional<Matcher> match(final Pattern pattern, final String key) {
        if (key == null) {
            return Optional.empty();
        }
        final Matcher matcher = pattern.matcher(key);
        return matcher.matches() ? Optional.of(matcher) : Optional.empty();
    }

    public record SelectKey(Long performanceId, Long seatId) {}

    public record HoldMetaKey(String holdKey) {}
}
