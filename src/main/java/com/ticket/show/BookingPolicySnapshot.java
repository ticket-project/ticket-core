package com.ticket.show;

import java.time.LocalDateTime;

/**
 * booking이 주문 생성 여부를 즉시 판단하는 데 필요한 회차 예매 정책의 불변 snapshot이다.
 *
 * <p>{@code bookingOpen}은 조회 시점 기준으로 이미 계산된 값이다. show JPA entity를 노출하지
 * 않는다.
 *
 * <p>좌석 가격({@code seatPrices})은 더 이상 이 snapshot이 갖지 않는다 — 주문 합계는 오직
 * booking이 소유한 {@code PerformanceSeat.unitPrice}로만 계산한다(ADR 0005). show 가격이
 * 바뀌어도 이미 만든 주문 금액이 바뀌지 않아야 하기 때문이다.
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
        boolean queueRequired
) {
}
