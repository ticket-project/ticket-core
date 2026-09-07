package com.ticket.booking.application.order.query.model;

import com.ticket.booking.domain.order.model.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * booking이 소유한 order/orderSeat 테이블만으로 조회한 주문상세 한 좌석분 행이다. show/venue/
 * grade/좌석 표시값은 주문 생성 시점에 Order/OrderSeat가 이미 snapshot한 값을 그대로 담는다
 * (ADR 0005) — show 표시값이 나중에 바뀌어도 이 값은 바뀌지 않는다. {@code memberId}로 회원의
 * 현재(live) 이름·이메일만 member 공개 API로 추가 조회한다.
 */
public record OrderDetailRow(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt,
        Long memberId,
        Long performanceId,
        String showTitleSnapshot,
        LocalDateTime performanceStartAtSnapshot,
        String venueNameSnapshot,
        Long performanceSeatId,
        Long seatId,
        BigDecimal unitPrice,
        String gradeCodeSnapshot,
        String gradeNameSnapshot,
        String seatLabelSnapshot
) {
}
