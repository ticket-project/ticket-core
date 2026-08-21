package com.ticket.core.domain.payment.model;

public enum PaymentStatus {
    READY("결제 준비"),
    APPROVED("결제 완료"),
    FAILED("결제 실패");

    private final String description;

    PaymentStatus(final String description) {
        this.description = description;
    }

    public String getCode() {
        return name();
    }

    public String getDescription() {
        return description;
    }
}
