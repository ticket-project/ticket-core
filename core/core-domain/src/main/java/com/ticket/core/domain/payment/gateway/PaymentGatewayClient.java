package com.ticket.core.domain.payment.gateway;

public interface PaymentGatewayClient {

    PaymentApprovalResult approve(PaymentApprovalCommand command);
}
