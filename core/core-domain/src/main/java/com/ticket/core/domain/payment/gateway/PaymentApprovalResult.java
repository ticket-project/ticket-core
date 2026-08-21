package com.ticket.core.domain.payment.gateway;

public record PaymentApprovalResult(
        boolean approved,
        String pgTransactionId,
        String failureCode,
        String failureMessage
) {

    public static PaymentApprovalResult approved(final String pgTransactionId) {
        return new PaymentApprovalResult(true, pgTransactionId, null, null);
    }

    public static PaymentApprovalResult declined(final String failureCode, final String failureMessage) {
        return new PaymentApprovalResult(false, null, failureCode, failureMessage);
    }
}
