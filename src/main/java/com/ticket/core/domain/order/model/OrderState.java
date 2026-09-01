package com.ticket.core.domain.order.model;

public enum OrderState {
    PENDING("결제 대기"),
    CONFIRMED("주문 확정"),
    EXPIRED("주문 만료"),
    CANCELED("주문 취소"),
    PAYMENT_FAILED("결제 실패");

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
