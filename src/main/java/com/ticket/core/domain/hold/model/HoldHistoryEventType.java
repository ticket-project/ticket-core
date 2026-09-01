package com.ticket.core.domain.hold.model;

public enum HoldHistoryEventType {
    CREATED("선점 생성"),
    CONFIRMED("주문 확정"),
    EXPIRED("선점 만료"),
    CANCELED("선점 취소");

    private final String description;

    HoldHistoryEventType(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
