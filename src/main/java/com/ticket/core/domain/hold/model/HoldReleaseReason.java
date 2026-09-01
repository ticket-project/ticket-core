package com.ticket.core.domain.hold.model;

public enum HoldReleaseReason {
    PAYMENT_CONFIRMED("결제 확정"),
    TTL_EXPIRED("TTL 만료"),
    USER_CANCELED("사용자 취소"),
    ORDER_EXPIRED("주문 만료"),
    PAYMENT_FAILED("결제 실패");

    private final String description;

    HoldReleaseReason(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
