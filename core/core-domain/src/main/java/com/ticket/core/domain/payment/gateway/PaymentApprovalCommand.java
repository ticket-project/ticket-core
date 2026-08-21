package com.ticket.core.domain.payment.gateway;

import com.ticket.core.domain.payment.model.PaymentMethod;

import java.math.BigDecimal;

public record PaymentApprovalCommand(
        String paymentKey,
        Long orderId,
        BigDecimal amount,
        PaymentMethod method
) {
}
