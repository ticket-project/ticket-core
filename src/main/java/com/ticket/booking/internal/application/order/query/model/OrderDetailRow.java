package com.ticket.booking.internal.application.order.query.model;

import com.ticket.booking.internal.domain.order.model.OrderState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * booking이 소유한 order/orderSeat 테이블만으로 조회한 주문상세 한 좌석분 행이다. catalog/identity
 * 표시값(공연·공연장·회원 이름 등)은 담지 않는다 — {@code GetOrderDetailUseCase}가 이 행의
 * {@code performanceId}·{@code memberId}·{@code seatId}로 그 module들의 공개 API를 조회해 합성한다.
 */
public record OrderDetailRow(
        String orderKey,
        OrderState status,
        LocalDateTime expiresAt,
        Long memberId,
        Long performanceId,
        Long performanceSeatId,
        Long seatId,
        BigDecimal price
) {
}
