package com.ticket.booking.exception;

import com.ticket.shared.exception.NotFoundException;

/**
 * 회차 판매 정책이 없다. 회차는 있는데 예매 정책이 아직 만들어지지 않은 경우를 포함한다.
 *
 * <p>{@code BookingException}이 아니라 {@link NotFoundException}을 상속한다 — 오류 코드 {@code E404}와 HTTP 404 매핑을 그대로 쓴다.
 */
public final class PerformanceSalesPolicyNotFoundException extends NotFoundException {
    public PerformanceSalesPolicyNotFoundException(final Long performanceId) {
        super("회차 판매 정책을 찾을 수 없습니다. id=" + performanceId);
    }
}
