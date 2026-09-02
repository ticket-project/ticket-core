package com.ticket.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * booking이 주문 생성 여부를 즉시 판단하는 데 필요한 회차 예매 정책의 불변 snapshot이다.
 *
 * <p>{@code bookingOpen}은 조회 시점 기준으로 이미 계산된 값이다. {@code seatPrices}는 호출자가 넘긴
 * seat ID에 한해서만 채워지며, catalog JPA entity를 노출하지 않는다.
 */
public record BookingPolicySnapshot(
        long performanceId,
        long showId,
        boolean bookingOpen,
        LocalDateTime orderOpenTime,
        LocalDateTime orderCloseTime,
        Integer maxCanHoldCount,
        Integer holdTime,
        String queueMode,
        String queueLevel,
        LocalDateTime preopenQueueStartAt,
        boolean queueRequired,
        Map<Long, BigDecimal> seatPrices
) {
    public BookingPolicySnapshot {
        seatPrices = Map.copyOf(seatPrices);
    }
}
