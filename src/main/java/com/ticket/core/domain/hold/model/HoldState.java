package com.ticket.core.domain.hold.model;

public enum HoldState {
    ACTIVE("선점 중"),
    CONFIRMED("주문 확정으로 종료"),
    EXPIRED("만료로 종료"),
    CANCELED("사용자 취소로 종료");

    private final String description;

    HoldState(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
