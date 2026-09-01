package com.ticket.core.domain.performanceseat.model;

public enum PerformanceSeatState {
    AVAILABLE("예매가능"),
    RESERVED("예매완료");

    private final String description;

    PerformanceSeatState(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
