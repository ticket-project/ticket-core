package com.ticket.booking.selection.infrastructure;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * selection 관련 Redis key 규칙을 한 곳에서 관리한다.
 *
 * <p>hold 관련 key(hold, holdMeta, holdSeatIndex, tryParseHoldMetaKey)는
 * {@code com.ticket.booking.infrastructure.HoldRedisKey}로 옮겼다 — selection과 hold는
 * 독립 개념이라(ADR 0001) hold key 파싱이 selection 소유 클래스에 있을 이유가 없었다.
 */
public final class SeatSelectionRedisKey {

    private static final String SELECT_KEY = "seat:select:{perf:%d}:%d";
    private static final String SELECT_SEAT_INDEX_KEY = "seat:select:index:{perf:%d}";

    private static final Pattern SELECT_KEY_PATTERN = Pattern.compile("^seat:select:\\{perf:(\\d+)}:(\\d+)$");

    private SeatSelectionRedisKey() {}

    public static String select(final Long perfId, final Long seatId) {
        return String.format(SELECT_KEY, perfId, seatId);
    }

    public static String selectSeatIndex(final Long perfId) {
        return String.format(SELECT_SEAT_INDEX_KEY, perfId);
    }

    public static Optional<SelectKey> tryParseSelectKey(final String key) {
        return match(SELECT_KEY_PATTERN, key)
                .map(matcher -> new SelectKey(
                        Long.parseLong(matcher.group(1)),
                        Long.parseLong(matcher.group(2))
                ));
    }

    private static Optional<Matcher> match(final Pattern pattern, final String key) {
        if (key == null) {
            return Optional.empty();
        }
        final Matcher matcher = pattern.matcher(key);
        return matcher.matches() ? Optional.of(matcher) : Optional.empty();
    }

    public record SelectKey(Long performanceId, Long seatId) {}
}
