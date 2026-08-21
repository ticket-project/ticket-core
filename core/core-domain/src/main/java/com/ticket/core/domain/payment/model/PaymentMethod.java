package com.ticket.core.domain.payment.model;

public enum PaymentMethod {
    CARD("카드");

    private final String description;

    PaymentMethod(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
