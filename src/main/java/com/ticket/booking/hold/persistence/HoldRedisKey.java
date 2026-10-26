package com.ticket.booking.hold.persistence;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * hold 관련 Redis key 규칙을 한 곳에서 관리한다.
 *
 * <p>원래는 {@code SeatSelectionRedisKey}(selection 소유)에 selection·hold 키가 함께 있었다. hold를 자기 패키지로 모으며 key 형식은 그대로 두고 파싱 책임만
 * 여기로 옮겼다 — 운영 중인 Redis key 문자열 형식은 바뀌지 않는다.
 */
public final class HoldRedisKey {
    private static final String HOLD_KEY = "seat:hold:{perf:%d}:%d";
    private static final String HOLD_SEAT_INDEX_KEY = "seat:hold:index:{perf:%d}";
    private static final String HOLD_META_KEY = "hold:key:%s";
    /** 주문 만료 장부. 값은 holdKey, 점수는 그 주문의 만료 시각(epoch ms)이다. Redis 한 대라 전역 키 하나를 쓴다 — 클러스터로 가면 회차별로 바꾼다(D2-2). */
    private static final String HOLD_EXPIRY_LEDGER_KEY = "hold:expiry:due";
    private static final Pattern HOLD_META_KEY_PATTERN = Pattern.compile("^hold:key:(.+)$");

    private HoldRedisKey() {}

    public static String hold(final Long perfId, final Long seatId) {
        return String.format(HOLD_KEY, perfId, seatId);
    }

    public static String holdMeta(final String holdKey) {
        return String.format(HOLD_META_KEY, holdKey);
    }

    public static String holdExpiryLedger() {
        return HOLD_EXPIRY_LEDGER_KEY;
    }

    public static String holdSeatIndex(final Long perfId) {
        return String.format(HOLD_SEAT_INDEX_KEY, perfId);
    }

    /** hold meta key면 그 안의 holdKey를 돌려준다. */
    public static Optional<String> tryParseHoldMetaKey(final String key) {
        final Matcher matcher = HOLD_META_KEY_PATTERN.matcher(key);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
