package com.ticket.catalog.domain.show;

public enum BookingStatus {
    BEFORE_OPEN("예매 오픈 전"),
    ON_SALE("예매중"),
    CLOSED("예매 종료");

    private final String description;

    BookingStatus(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
