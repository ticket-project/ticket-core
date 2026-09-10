package com.ticket.booking.order.domain;

/**
 * ADR 0005: {@code PAYMENT_FAILED}는 제거됐다. Payment는 Order에 대한 여러 결제 시도 중 하나이고
 * (Order 1 : 0..N Payment), 결제 시도 한 번이 실패해도 Order는 만료 전까지 다시 결제를 시도할 수
 * 있어야 한다. 결제 실패는 Payment 자신의 상태이지 Order를 끝내는 사건이 아니다.
 */
public enum OrderState {
    PENDING("결제 대기"),
    CONFIRMED("주문 확정"),
    EXPIRED("주문 만료"),
    CANCELED("주문 취소");

    private final String description;

    OrderState(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
