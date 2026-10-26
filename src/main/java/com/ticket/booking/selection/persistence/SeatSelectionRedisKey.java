package com.ticket.booking.selection.persistence;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * selection 관련 Redis key 규칙을 한 곳에서 관리한다.
 *
 * <p>hold 관련 key(hold, holdMeta, holdSeatIndex, tryParseHoldMetaKey)는
 * {@code com.ticket.booking.hold.persistence.HoldRedisKey}로 옮겼다 — selection과 hold는 독립 개념이라(ADR 0001) hold key 파싱이
 * selection 소유 클래스에 있을 이유가 없었다.
 */
public final class SeatSelectionRedisKey {

    private static final String SELECT_KEY = "seat:select:{perf:%d}:%d";
    private static final String SELECT_SEAT_INDEX_KEY = "seat:select:index:{perf:%d}";
    private static final String SELECT_MEMBER_INDEX_KEY = "seat:select:member:{perf:%d}:%s";

    private static final Pattern SELECT_KEY_PATTERN = Pattern.compile("^seat:select:\\{perf:(\\d+)}:(\\d+)$");

    private SeatSelectionRedisKey() {}

    public static String select(final Long perfId, final Long seatId) {
        return String.format(SELECT_KEY, perfId, seatId);
    }

    public static String selectSeatIndex(final Long perfId) {
        return String.format(SELECT_SEAT_INDEX_KEY, perfId);
    }

    /** 회원 한 명이 이 회차에서 선택 중인 좌석 인덱스. 좌석 키와 같은 hash slot({perf:N})에 두어야 Lua 한 번으로 함께 바꿀 수 있다. */
    public static String selectMemberIndex(final Long perfId, final String memberId) {
        return String.format(SELECT_MEMBER_INDEX_KEY, perfId, memberId);
    }

    public static Optional<SelectKey> tryParseSelectKey(final String key) {
        final Matcher matcher = SELECT_KEY_PATTERN.matcher(key);
        return matcher.matches()
                ? Optional.of(new SelectKey(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))))
                : Optional.empty();
    }

    public record SelectKey(Long performanceId, Long seatId) {}
}
