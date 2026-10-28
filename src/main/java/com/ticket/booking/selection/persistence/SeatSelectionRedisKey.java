package com.ticket.booking.selection.persistence;

/**
 * selection 관련 Redis key 규칙을 한 곳에서 관리한다.
 *
 * <p>hold 관련 key(hold, holdMeta, holdSeatIndex)는 {@code com.ticket.booking.hold.persistence.HoldRedisKey}로 옮겼다 —
 * selection과 hold는 독립 개념이라(ADR 0001) hold key 파싱이 selection 소유 클래스에 있을 이유가 없었다.
 */
public final class SeatSelectionRedisKey {

    private static final String SELECT_KEY = "seat:select:{perf:%d}:%d";
    private static final String SELECT_SEAT_INDEX_KEY = "seat:select:index:{perf:%d}";
    private static final String SELECT_MEMBER_INDEX_KEY = "seat:select:member:{perf:%d}:%s";

    /**
     * 좌석 선택 만료 장부. 전역 Sorted Set 하나다(값 "회차 id:좌석 id", 점수 Redis {@code TIME} 기준 선택 만료 시각 ms).
     *
     * <p>ponytail: 회차 hash tag가 없어 Redis Cluster에서는 선택 키와 다른 slot이라 선택 스크립트 한 번에 쓸 수 없다. 지금은 Redis 한 대다. Cluster로 가면
     * 회차별 장부와 회차 목록으로 바꾼다(스케일 아웃 결정 D2-2, 2026-10-26 설계 {@code cde8a578}).
     */
    public static final String SELECT_EXPIRY_LEDGER_KEY = "seat:select:expiry";

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

    /** 장부 값. 같은 좌석을 다시 고르면 항목이 늘지 않고 점수만 바뀐다. */
    public static String selectExpiryEntry(final Long perfId, final Long seatId) {
        return perfId + ":" + seatId;
    }
}
